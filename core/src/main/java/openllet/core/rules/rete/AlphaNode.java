// Copyright (c) 2006 - 2008, Clark & Parsia, LLC. <http://www.clarkparsia.com>
// This source code is available under the terms of the Affero General Public License v3.
//
// Please see LICENSE.txt for full license terms, including the availability of proprietary exceptions.
// Questions, comments, or requests for clarification: licensing@clarkparsia.com

package openllet.core.rules.rete;

import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Level;
import openllet.aterm.ATermAppl;
import openllet.core.ABox;
import openllet.core.Node;
import openllet.core.rules.model.RuleAtom;
import openllet.core.utils.ATermUtils;

/**
 */
public abstract class AlphaNode extends ReteNode
{
	protected static final Iterator<WME> NO_MATCH = Collections.<WME> emptyList().iterator();

	protected boolean _doExplanation;

	protected final ABox _abox;

	public AlphaNode(final ABox abox)
	{
		this._abox = abox;
	}

	public abstract Iterator<WME> getMatches(int argIndex, Node arg);

	public abstract Iterator<WME> getMatches();

	public abstract boolean matches(RuleAtom atom);

	protected Node initNode(final ATermAppl name)
	{
		if (ATermUtils.isLiteral(name))
			return _abox.addLiteral(name);
		else
		{
			_abox.copyOnWrite();
			return _abox.getIndividual(name);
		}
	}

	protected void activate(final WME wme)
	{
		if (_logger.isLoggable(Level.FINE))
			_logger.fine("Activate alpha " + wme);
		for (final BetaNode beta : getBetas())
			beta.activate(wme);
	}

	public void setDoExplanation(final boolean doExplanation)
	{
		this._doExplanation = doExplanation;
	}

	@Override
	public void print(final String indent)
	{
		for (final BetaNode node : getBetas())
			if (node.isTop())
				node.print(indent);
	}
}