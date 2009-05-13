package history;

import model.*;
import model.Messages.GraphConstraints;
import model.Messages.GraphMessage;

import io.GeneralPreferences;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

/**
 * This class tacks all actions happening to a graph and saves the last few ones.
 * Depending on GeneralPreferences, the 
 * - Number of Actions to be undo- or redoable may vary
 * - Tracking of Selection-Changes may be active or inactive
 * 
 * It also provides Undo(), Redo() and the methods to check their possibility
 * 
 * This Observer may only subscribe to Observables that sends GraphMessages.
 * @author Ronny Bergmann
 * @since 0.3
 */
public class HyperEdgeShapeHistoryManager extends CommonGraphHistoryManager
{	
	/**
	 * Create a new GraphHistoryManager for the given VHyperGraph
	 * @param vhg the HyperGraph, that should be extended with a History
	 */
	public HyperEdgeShapeHistoryManager(VHyperGraph vhg)
	{
		super(vhg);
		trackedGraph = vhg;
		lastGraph = vhg.clone();
		CommonInitialization();
	}
	/**
	 * Create an Action based on the message, that came from the Graph,
	 * return that Action and update LastGraph
	 * @param m the created Action
	 * @return
	 */
	private CommonGraphAction handleSingleAction(GraphMessage m, Vector<Object> param)
	{
		if (m.getModification()==(GraphConstraints.UPDATE|GraphConstraints.HYPEREDGESHAPE|GraphConstraints.CREATION))
		{ //Only case handled here, HyperEdgeShapeCreation
			try {
			return new HyperEdgeShapeAction(
				param, //These Parameters
				((m.getModification()&GraphConstraints.LOCAL) == GraphConstraints.LOCAL), //Local Action?
				((VHyperGraph)lastGraph).modifyHyperEdges.get(m.getElementID()),
				m.getModification(), //All known action stuff
				(VHyperGraph)lastGraph);
			}
			catch (GraphActionException e)
			{
				System.err.println("DEBUG: HyperEdgeshape (#"+m.getElementID()+") Action ("+m.getModification()+") Failed:"+e.getMessage());
				return null;
			}
		}
		return super.handleSingleAction(m);
	}
	/**
	 * Add Tracked Action to the Undo-Stuff
	 * Create an Action based on tracked Message
	 * @param m
	 */
	private void addAction(GraphMessage m, Vector<Object> param)	{	
		if ((m.getModification()&GraphConstraints.BLOCK_ABORT)==GraphConstraints.BLOCK_ABORT)
			return; //Don't handle Block-Abort-Stuff
		CommonGraphAction act = null;
		if (m.getElementID() > 0) //Message for single stuff thats the only case of our interest
		{
			act = handleSingleAction(m,param);	
			if (act==null)
				return;
			clonetrackedGraph();
			if (!RedoStack.isEmpty())
				RedoStack.clear();
			if (UndoStack.size()>=stacksize)
			{	//Now it can't get Unchanged by Undo
				SavedUndoStackSize--;
				UndoStack.remove();
			}
			UndoStack.add(act);

		}
		else //multiple modifications, up to know just a replace */
			super.addAction(m);
	}


	public void update(Observable o, Object arg)
	{
		GraphMessage temp = null;
		if (Blockstart!=null)
			temp = Blockstart.clone();
		//Handle normal stuff
		super.update(o, arg);
		
		GraphMessage m = (GraphMessage)arg;
		if (m==null)
			return;
		if (m.getModification()==GraphConstraints.HISTORY) //Ignore them, they'Re from us or another stupid history
			return;
		//Complete Replacement of Graphor Hypergraph Handling (Happens when loading a new graph
		GraphMessage actualAction;
		if ((blockdepth==0)&&(active) //super.update ended a block or we are active either way
		   && 
		   ( ((m.getModification()&GraphConstraints.BLOCK_END)==GraphConstraints.BLOCK_END))
		   || ((m.getModification()&GraphConstraints.BLOCK_ABORT)==GraphConstraints.BLOCK_ABORT))
		{ //Then a block ended
			actualAction = temp;
		}
		else
			actualAction = m;
		if (((m.getModification() & GraphConstraints.BLOCK_ABORT)==GraphConstraints.BLOCK_ABORT) || (temp==null) || (!active))
			return;

		if ((actualAction.getModifiedElementTypes()==GraphConstraints.HYPEREDGE)
			&& (actualAction.getModification() == (GraphConstraints.UPDATE|GraphConstraints.HYPEREDGESHAPE|GraphConstraints.CREATION))
			&& (actualAction.getElementID() != 0)
			&& (actualAction.getAffectedElementTypes() ==GraphConstraints.HYPEREDGE))
			
		{// The type of action we want to track here - than it was tracked wrong before
			UndoStack.removeLast(); //Undo the undo-push from superclass

			addAction(m); //Do our action upon that
		}
	}
}
