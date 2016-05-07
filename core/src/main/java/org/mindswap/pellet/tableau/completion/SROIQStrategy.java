// Copyright (c) 2006 - 2008, Clark & Parsia, LLC. <http://www.clarkparsia.com>
// This source code is available under the terms of the Affero General Public
// License v3.
//
// Please see LICENSE.txt for full license terms, including the availability of
// proprietary exceptions.
// Questions, comments, or requests for clarification: licensing@clarkparsia.com

package org.mindswap.pellet.tableau.completion;

import com.clarkparsia.pellet.expressivity.Expressivity;
import java.util.List;
import java.util.logging.Level;
import org.mindswap.pellet.ABox;
import org.mindswap.pellet.IndividualIterator;
import org.mindswap.pellet.PelletOptions;
import org.mindswap.pellet.exceptions.InternalReasonerException;
import org.mindswap.pellet.tableau.branch.Branch;
import org.mindswap.pellet.tableau.completion.rule.TableauRule;

/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2006
 * </p>
 * <p>
 * Company: Clark & Parsia, LLC. <http://www.clarkparsia.com>
 * </p>
 *
 * @author Evren Sirin
 */
public class SROIQStrategy extends CompletionStrategy
{
	public SROIQStrategy(final ABox abox)
	{
		super(abox);
	}

	protected boolean backtrack()
	{
		boolean branchFound = false;
		abox.stats.backtracks++;
		while (!branchFound)
		{
			completionTimer.check();

			final int lastBranch = abox.getClash().getDepends().max();

			// not more branches to try
			if (lastBranch <= 0)
				return false;
			else
				if (lastBranch > abox.getBranches().size())
					throw new InternalReasonerException("Backtrack: Trying to backtrack to _branch " + lastBranch + " but has only " + abox.getBranches().size() + " branches. Clash found: " + abox.getClash());
				else
					if (PelletOptions.USE_INCREMENTAL_DELETION)
					{
						// get the last _branch
						final Branch br = abox.getBranches().get(lastBranch - 1);

						// if this is the last _disjunction, merge pair, etc. for the
						// _branch (i.e, br.tryNext == br.tryCount-1) and there are no
						// other branches to test (ie.
						// _abox.getClash().depends.size()==2),
						// then update depedency index and return false
						if ((br.getTryNext() == br.getTryCount() - 1) && abox.getClash().getDepends().size() == 2)
						{
							abox.getKB().getDependencyIndex().addCloseBranchDependency(br, abox.getClash().getDepends());
							return false;
						}
					}

			final List<Branch> branches = abox.getBranches();
			abox.stats.backjumps += (branches.size() - lastBranch);
			// CHW - added for incremental deletion support
			if (PelletOptions.USE_TRACING && PelletOptions.USE_INCREMENTAL_CONSISTENCY)
			{
				// we must clean up the KB dependecny index
				final List<Branch> brList = branches.subList(lastBranch, branches.size());
				for (final Branch branch : brList)
					// remove from the dependency index
					abox.getKB().getDependencyIndex().removeBranchDependencies(branch);
				brList.clear();
			}
			else
				// old approach
				branches.subList(lastBranch, branches.size()).clear();

			// get the _branch to try
			final Branch newBranch = branches.get(lastBranch - 1);

			if (log.isLoggable(Level.FINE))
				log.fine("JUMP: Branch " + lastBranch);

			if (lastBranch != newBranch.getBranch())
				throw new InternalReasonerException("Backtrack: Trying to backtrack to _branch " + lastBranch + " but got " + newBranch.getBranch());

			// set the last clash before restore
			if (newBranch.getTryNext() < newBranch.getTryCount())
				newBranch.setLastClash(abox.getClash().getDepends());

			// increment the counter
			newBranch.setTryNext(newBranch.getTryNext() + 1);

			// no need to restore this _branch if we exhausted possibilities
			if (newBranch.getTryNext() < newBranch.getTryCount())
				// undo the changes done after this _branch
				restore(newBranch);

			// try the next possibility
			branchFound = newBranch.tryNext();

			if (!branchFound)
				if (log.isLoggable(Level.FINE))
					log.fine("FAIL: Branch " + lastBranch);
		}

		return branchFound;
	}

	@Override
	public void complete(final Expressivity expr)
	{
		initialize(expr);

		while (!abox.isComplete())
		{
			while (abox.isChanged() && !abox.isClosed())
			{
				completionTimer.check();

				abox.setChanged(false);

				if (log.isLoggable(Level.FINE))
				{
					log.fine("Branch: " + abox.getBranch() + ", Depth: " + abox.stats.treeDepth + ", Size: " + abox.getNodes().size() + ", Mem: " + (Runtime.getRuntime().freeMemory() / 1000) + "kb");
					abox.validate();
					printBlocked();
					abox.printTree();
				}

				final IndividualIterator i = (PelletOptions.USE_COMPLETION_QUEUE) ? abox.getCompletionQueue() : abox.getIndIterator();

						// flush the _queue
						if (PelletOptions.USE_COMPLETION_QUEUE)
							abox.getCompletionQueue().flushQueue();

						for (final TableauRule tableauRule : tableauRules)
				{
							tableauRule.apply(i);
							if (abox.isClosed())
								break;
						}

						// it could be the case that there was a clash and we had a
						// deletion update that retracted it
						// however there could have been some thing on the _queue that
						// still needed to be refired from backtracking
						// so onle set that the _abox is clash free after we have applied
						// all the rules once
						if (PelletOptions.USE_COMPLETION_QUEUE)
							abox.getCompletionQueue().setClosed(abox.isClosed());
			}

			if (abox.isClosed())
			{
				if (log.isLoggable(Level.FINE))
					log.fine("Clash at Branch (" + abox.getBranch() + ") " + abox.getClash());

				if (backtrack())
				{
					abox.setClash(null);

					if (PelletOptions.USE_COMPLETION_QUEUE)
						abox.getCompletionQueue().setClosed(false);
				}
				else
				{
					abox.setComplete(true);

					// we need to flush the _queue to add the other elements
					if (PelletOptions.USE_COMPLETION_QUEUE)
						abox.getCompletionQueue().flushQueue();
				}
			}
			else
				if (PelletOptions.SATURATE_TABLEAU)
				{
					Branch unexploredBranch = null;
					for (int i = abox.getBranches().size() - 1; i >= 0; i--)
					{
						unexploredBranch = abox.getBranches().get(i);
						unexploredBranch.setTryNext(unexploredBranch.getTryNext() + 1);
						if (unexploredBranch.getTryNext() < unexploredBranch.getTryCount())
						{
							restore(unexploredBranch);
							System.out.println("restoring _branch " + unexploredBranch.getBranch() + " _tryNext = " + unexploredBranch.getTryNext() + " _tryCount = " + unexploredBranch.getTryCount());
							unexploredBranch.tryNext();
							break;
						}
						else
						{
							System.out.println("removing _branch " + unexploredBranch.getBranch());
							abox.getBranches().remove(i);
							unexploredBranch = null;
						}
					}
					if (unexploredBranch == null)
						abox.setComplete(true);
				}
				else
					abox.setComplete(true);
		}

	}

}
