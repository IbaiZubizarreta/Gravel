package model;

import java.util.BitSet;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Observable;

import model.Messages.GraphMessage;
/**
 * MGraph
 * 
 * The pure mathematical graph
 * 
 * every node is represented by an index and its name
 * 
 * every edge is represened by its index, a start and an endnode, a value and its name
 * 
 * every subgraph contains its index, a name and the information which nodes and egdes are in this subgraph
 * 
 * in addition the mgraph contains information whether loops (startnode==endnode) or multiple edges between 2 nodes are allowed
 * This class is called from multiple threads, so its implemented threadsafe 
 *   
 * @author Ronny Bergmann
 *
 */

public class MGraph extends Observable
{	
	private HashSet<MNode> mNodes;
	private HashSet<MEdge> mEdges;
	private HashSet<MSubgraph> mSubgraphs;
	private boolean directed, allowloops, allowmultiple = false;
	Lock EdgeLock, NodeLock;
	/**
	 * Create a new Graph where
	 * @param d indicates whether edges are directed (true) or not (false
	 * @param l indicates whether loops are allowed or not
	 * @param m indicates whether multiple edges between two nodes are allowed
	 */
	public MGraph(boolean d, boolean l, boolean m)
	{
		mNodes = new HashSet<MNode>();
		mEdges = new HashSet<MEdge>();
		mSubgraphs = new HashSet<MSubgraph>();
		EdgeLock = new ReentrantLock();
		NodeLock = new ReentrantLock();
		directed = d;
		allowloops =l;
		allowmultiple = m;
	}
	/**
	 * clone this graph and 
	 * @return the copy
	 */
	public MGraph clone()
	{
		MGraph clone = new MGraph(directed,allowloops, allowmultiple);
		//Untergraphen
		Iterator<MSubgraph> n1 = mSubgraphs.iterator();
		while (n1.hasNext())
			clone.addSubgraph(n1.next().clone()); //Jedes Set kopieren
		//Knoten
		Iterator<MNode> n2 = mNodes.iterator();
		while (n2.hasNext())
		{
			MNode actualNode = n2.next();
			MNode Nodeclone = new MNode(actualNode.index, actualNode.name);
			clone.addNode(Nodeclone);
			//In alle Sets einfuegen
			n1 = mSubgraphs.iterator();
			while (n1.hasNext())
			{
				MSubgraph actualSet = n1.next();
				if (actualSet.containsNode(actualNode.index))
					clone.addNodetoSubgraph(actualNode.index,actualSet.getIndex()); //Jedes Set kopieren
			}
		}
		//Analog Kanten
		Iterator<MEdge> n3 = mEdges.iterator();
		while (n3.hasNext())
		{
			MEdge actualEdge = n3.next();
			MEdge cEdge = new MEdge(actualEdge.index, actualEdge.StartIndex, actualEdge.EndIndex, actualEdge.Value, actualEdge.name);
			clone.addEdge(cEdge);
			//In alle Sets einfuegen
			n1 = mSubgraphs.iterator();
			while (n1.hasNext())
			{
				MSubgraph actualSet = n1.next();
				if (actualSet.containsEdge(actualEdge.index))
					clone.addEdgetoSubgraph(actualEdge.index,actualSet.getIndex()); //Jedes Set kopieren
			}
		}
		//und zurückgeben
		return clone;
	}
	 /** informs all subscribers about a change. This Method is used to push a notify from outside
	 * mit dem Oject o als Parameter
	 */
	public void pushNotify(Object o) {
		setChanged();
		if (o == null)
			notifyObservers();
		else
			notifyObservers(o);
	}	
	/**
	 * Indicator whether the graph is directed or not
	 * @return
	 */
	public boolean isDirected()
	{
		return directed;
	}
	/**
	 * Set the graph to directed or not. if the graph is set to non-directed, all 
	 * @param d
	 */
	public BitSet setDirected(boolean d)
	{
		BitSet removed = new BitSet();
		if (d==directed)
			return removed; //nicht geändert
		//Auf gerihctet umstellen ist kein Problem. 
		if ((!d)&&(!allowmultiple)) //if multiple edges are allowed we don't need to delete them
									//auf ungerichtet umstellen, existieren Kanten i->j und j->i so lösche eine
		{
			NodeLock.lock(); //Knoten finden
			try
			{
				Iterator<MNode> n = mNodes.iterator();				
				while (n.hasNext())
				{
					MNode t = n.next();
					Iterator<MNode> n2 = mNodes.iterator();
					while (n2.hasNext())
					{
						MNode t2 = n2.next();
						if (t.index <= t2.index)
						{
							Vector<Integer> ttot2 = getEdgeIndices(t.index,t2.index);
							Vector<Integer> t2tot = getEdgeIndices(t2.index,t.index);
							//In the nonmultiple case each Vector has exactely one or no edge in it
							if ((!ttot2.isEmpty())&&(!t2tot.isEmpty()))
							{
								int e1 = ttot2.firstElement();
								int e2 = t2tot.firstElement();
								MEdge m = getEdge(e2);
								m.Value = getEdge(e2).Value+getEdge(e1).Value;
								removeEdge(e1);
								removed.set(e1);
							}
						}
					}
				}
			}
			finally {NodeLock.unlock();}
		}
		directed = d;
		setChanged();
		notifyObservers(
				new GraphMessage(GraphMessage.EDGE|GraphMessage.DIRECTION, //Type
								GraphMessage.UPDATE) //Status 
			);

		return removed;
	}
	/**
	 * Indicates whether loops are allowed or not
	 * @return true if loops are allowed, else false
	 */
	public boolean isLoopAllowed() {
		return allowloops;
	}
	/**
	 * Set the Indicator for loops to a new value. If they are disabled, all loops are removed and 
	 * @param a the new value for the indicator
	 * @return a bitset of all removed edges (if switched loops of)
	 */
	public BitSet setLoopsAllowed(boolean a) 
	{
		BitSet removed = new BitSet();
		if ((allowloops)&&(!a)) //disbabling
		{
			EdgeLock.lock();
			try
			{
				HashSet<MEdge> deledges = new HashSet<MEdge>();
				Iterator<MEdge> n2 = mEdges.iterator();
				while (n2.hasNext())
				{
					MEdge e = n2.next();
					removed.set(e.index, e.EndIndex==e.StartIndex); //Set if Loop, clear else
					if (removed.get(e.index)) //was is just set?
						deledges.add(e);
				}
				Iterator<MEdge> n3 = deledges.iterator();
				while (n3.hasNext()) // Diese loeschen
					mEdges.remove(n3.next());
			} finally {EdgeLock.unlock();}
		}	
		this.allowloops = a;
		setChanged();
		notifyObservers(new GraphMessage(GraphMessage.LOOPS,GraphMessage.UPDATE,GraphMessage.EDGE));	
		return removed;
	}
	/**
	 * Indicates whether multiple edges between two nodes are allowed
	 * @return
	 */
	public boolean isMultipleAllowed() {
		return allowmultiple;
	}
	/**
	 * Set the possibility of multiple edges to the new value
	 * If multiple edges are disabled, the multiple edges are removed and the edge values between two nodes are added
	 * @param a
	 * @return a BitSet where alle indices of deleted edges are set true
	 */
	public BitSet setMultipleAllowed(boolean a) 
	{
		BitSet removed = new BitSet();
		if ((allowmultiple)&&(!a)) //Changed from allowed to not allowed, so remove all multiple
		{	
			NodeLock.lock(); //Knoten finden
			try
			{
				Iterator<MNode> n = mNodes.iterator();				
				while (n.hasNext())
				{
					MNode t = n.next();
					Iterator<MNode> n2 = mNodes.iterator();
					while (n2.hasNext())
					{
						MNode t2 = n2.next();
						//if the graph is directed
						if (((!directed)&&(t2.index<=t.index))||(directed)) //in the nondirected case only half the cases
						{
							if (EdgesBetween(t.index,t2.index)>1) //we have to delete
							{
								Vector<Integer> multipleedges = getEdgeIndices(t.index,t2.index);
								int value = getEdge(multipleedges.firstElement()).Value;
								//Add up the values and remove the edges from the second to the last
								Iterator<Integer> iter = multipleedges.iterator();
								iter.next();
								while(iter.hasNext())
								{
									int nextindex = iter.next();
									value += getEdge(nextindex).Value;
									removeEdge(nextindex);
									removed.set(nextindex);
								}
								getEdge(multipleedges.firstElement()).Value = value;
							}
						}					
					}
				}
			}
			finally {NodeLock.unlock();}
		}
		this.allowmultiple = a;
		setChanged();
		notifyObservers(new GraphMessage(GraphMessage.MULTIPLE,GraphMessage.UPDATE,GraphMessage.EDGE));	
		return removed;
	}
	/*
	 * Knotenfunktionen
	 */
	/**
	 * Adds a new node to the graph with
	 * @param m as the new MNode
	 */
	public void addNode(MNode m)
	{
		if (getNode(m.index)!=null)
			return;
		NodeLock.lock();
		try 
		{
			mNodes.add(m);
			setChanged();
			notifyObservers(new GraphMessage(GraphMessage.NODE,m.index,GraphMessage.ADDITION,GraphMessage.NODE));	
		} 
		finally {NodeLock.unlock();}
	}
	/**
	 * Replace (if existent) the node in the graph with the index of the parameter node by the parameter
	 * 
	 * @param node new node for its index
	 */
	public void replaceNode(MNode node)
	{
		MNode oldnode = getNode(node.index);
		if (oldnode==null)
			return;
		NodeLock.lock();
		try 
		{
			mNodes.remove(oldnode);
			mNodes.add(node);
			setChanged();
			notifyObservers(new GraphMessage(GraphMessage.NODE,node.index,GraphMessage.UPDATE,GraphMessage.NODE));	
		} 
		finally
		{NodeLock.unlock();}		
	}
	/**
	 * Change the index of a node. This method is neccessary, because all other functions rely on the fact, 
	 * that the nodeindex is the reference for everything
	 * 
	 * @param oldi old index of the node
	 * @param newi new index of the node
	 */
	public void changeNodeIndex(int oldi, int newi)
	{
		if (oldi==newi)
			return;
		MNode oldn = getNode(oldi);
		MNode newn = getNode(newi);
		if ((oldn==null)||(newn!=null))
			return; //can't change
		//Change adjacent edges
		EdgeLock.lock();
		try //Find adjacent adjes and update index
		{		Iterator<MEdge> ei = mEdges.iterator();
				while (ei.hasNext())
				{
					MEdge e = ei.next();
					if (e.EndIndex==oldi)
						e.EndIndex=newi;
					if (e.StartIndex==oldi)
						e.StartIndex=newi;
				}
		}
		finally {EdgeLock.unlock();}
		//Update Subgraphs
		Iterator<MSubgraph> iter = mSubgraphs.iterator();
		while (iter.hasNext())
		{
			MSubgraph actual = iter.next();
			if (actual.containsNode(oldi))
			{
				actual.removeNode(oldi);
				actual.addNode(newi);
			}
		}
		//And Change the oldnode aswell
		oldn.index=newi;
		replaceNode(newn);
		setChanged();
		notifyObservers(new GraphMessage(GraphMessage.NODE, GraphMessage.REPLACEMENT, GraphMessage.ALL_ELEMENTS));	
	}
	/**
	 * remove a node from the graph. thereby the adjacent edges are removed too. The indices of the deleted edges
	 * are set in the return value
	 * 
	 * @param i index of the node, that should be removed
	 * @return a bitset where all edge indices are set true, that are adjacent and were deleted too
	 */
	public BitSet removeNode(int i)
	{
		MNode toDel = getNode(i);
		BitSet ergebnis = new BitSet();
		if (toDel==null)
			return ergebnis; //Nothing to delete
		//remove from all Subsets
		Iterator<MSubgraph> iter = mSubgraphs.iterator();
		while (iter.hasNext())
		{
			MSubgraph actual = iter.next();
			if (actual.containsNode(i))
				actual.removeNode(i);
		}
		EdgeLock.lock();
		try
		{ //Find adjacent edges and save them in the deledges Hashset
				HashSet<MEdge> deledges = new HashSet<MEdge>();
				Iterator<MEdge> n2 = mEdges.iterator();
				while (n2.hasNext())
				{
					MEdge e = n2.next();
					if ((e.EndIndex==i)||(e.StartIndex==i))
					{
						ergebnis.set(e.index);
						deledges.add(e);
					}
					else
						ergebnis.clear(e.index);
				}
				Iterator<MEdge> n3 = deledges.iterator();
				while (n3.hasNext()) // Diese loeschen
				{
					mEdges.remove(n3.next());
				}
				NodeLock.lock();
				try
				{
					mNodes.remove(toDel); //und den Knoten loeschen
				}finally {NodeLock.unlock();}
		}
		finally {EdgeLock.unlock();}
		setChanged();
		notifyObservers(new GraphMessage(GraphMessage.NODE,i,GraphMessage.REMOVAL,GraphMessage.ALL_ELEMENTS));	
		return ergebnis;
	}
	/**
	 * @return max node index +1
	 */
	public int getNextNodeIndex()
	{
		int index = 1;
		NodeLock.lock();
		try {
			Iterator<MNode> n = mNodes.iterator();
			while (n.hasNext()) {
				MNode temp = n.next();
				if (temp.index >= index) // index vergeben
				{
					index = temp.index + 1;
				}
			}
		} finally {
			NodeLock.unlock();
		}
		return index;
	}
	/**
	 * reurns the node name of the 
	 * @param i node with index i
	 * @return the node name as string
	 */
	public MNode getNode(int i)
	{
		NodeLock.lock();
		try
		{
			Iterator<MNode> n = mNodes.iterator();
			while (n.hasNext())
			{
				MNode t = n.next();
				if (t.index==i)
					return t;
			}
		} finally {NodeLock.unlock();}
		return null;
	}
	/**
	 * get a list of the node names in a vector, where each node name is stored at it's index
	 * every other component of the vector is null
	 * 
	 * TODO: Move to Mathematical Graph
	 * @return a Vector of all node names, 
	 */
	public Vector<String> getNodeNames() {
		Vector<String> ret = new Vector<String>();
		Iterator<MNode> n = mNodes.iterator();
		while (n.hasNext()) {
			MNode actual = n.next();
			if ((actual.index + 1) > ret.size()) {
				ret.setSize(actual.index + 1);
			}
			if (actual.index!=0) //kein temp-knoten
				ret.set(actual.index, getNode(actual.index).name);
		}
		return ret;
	}
	/**
	 * Returns the number of nodes contained in the graph
	 * @return 
	 */
	public int NodeCount()
	{
		return mNodes.size();
	}	
	/**
	 * Returns an Iterator to iterate the nodes
	 * @return a new iterator for the nodes
	 */
	public Iterator<MNode> getNodeIterator()
	{
			return mNodes.iterator();
	}
	/*
	 * Kantenfunktionen
	 */
	/**
	 * Add an edge with index i between s and e width value v
	 * If an edge exists between s and e, a new edge is only added if multiple edges are allowed
	 * If start and end are equal, the edge is only added if loops are allowed
	 *  
	 * If itis possible to add this edge, a copy of the parameter is added
	 * 
	 * @param e the new edge
	 * 
	 * @return true if the edge is added, else false
	 */
	public boolean addEdge(MEdge e)
	{
		if ((e.StartIndex==e.EndIndex)&&(!allowloops)) //adding tries a loop but loops are not allowed
			return false;
		if ((EdgesBetween(e.StartIndex, e.EndIndex)>0)&&(!allowmultiple)) //adding tries a second edge between s and e and multiple edges are not allowed
			return false;
		if (getEdge(e.index)!=null) //index already in use
			return false;
		EdgeLock.lock();
		try 
		{
			mEdges.add(new MEdge(e.index, e.StartIndex, e.EndIndex, e.Value, e.name));
			setChanged();
			notifyObservers(new GraphMessage(GraphMessage.EDGE,e.index,GraphMessage.ADDITION,GraphMessage.EDGE));	
		} 
		finally
		{
			EdgeLock.unlock();
		}
		return true;
	}
	/**
	 * Replace the an edge in the graph
	 * The index may not be changed, so the edge, that is replaced (if existent)
	 * is identfied by the index of the parameter edge given
	 * 
	 * @param edge Replacement for the edge in the graph with same index
	 */
	public void replaceEdge(MEdge edge)
	{
		EdgeLock.lock();
		try {
			Iterator<MEdge> ei = mEdges.iterator();
			while (ei.hasNext()) {
				MEdge temp = ei.next();
				if (temp.index == edge.index) // index vergeben
				{
					mEdges.remove(temp);
					mEdges.add(edge);
					setChanged();
					notifyObservers(new GraphMessage(GraphMessage.EDGE,edge.index,GraphMessage.UPDATE,GraphMessage.EDGE));	
					break;
				}
			}
		}
		finally {EdgeLock.unlock();}
	}
	/**
	 * Remove an edge from the graph.
	 * If it does not exist, nothing happens
	 * @param i edge defined by index
	 */
	public void removeEdge(int i)
	{
		MEdge toDel = getEdge(i);
		if (toDel!=null)
		{
			//remove from all Subsets
			Iterator<MSubgraph> iter = mSubgraphs.iterator();
			while (iter.hasNext())
			{
				MSubgraph actual = iter.next();
				if (actual.containsEdge(i))
					actual.removeEdge(i);
			}
			mEdges.remove(toDel);
			setChanged();
			notifyObservers(new GraphMessage(GraphMessage.EDGE,i,GraphMessage.REMOVAL,GraphMessage.EDGE));	
		}
	}
	/**
	 * Get the Mathematical Edge with index i
	 * @param i index of edge
	 * @return the edge if an edge with this index exists, else null
	 */
	public MEdge getEdge(int i)
	{
		Iterator<MEdge> n = mEdges.iterator();
		while (n.hasNext())
		{
			MEdge e = n.next();
			if (e.index==i)
			{
				return e;
			}
		}
		return null;
	}
	/**
	 * get a free edge index
	 * @return max_exge_index + 1
	 */
	public int getNextEdgeIndex()
	{
		EdgeLock.lock();
		int index = 1;
		try {
			Iterator<MEdge> n = mEdges.iterator();
			while (n.hasNext()) {
				MEdge temp = n.next();
				if (temp.index >= index) // index vergeben
				{
					index = temp.index + 1;
				}
			}
		} finally {
			EdgeLock.unlock();
		}
		return index;
	}
	/**
	 * get a list of the edge names in a vector, where each edge name is stored at it's index
	 * every other component of the vector is null
	 * <br><br>
	 * an edge name is the mathematical given edge name 
	 * <br><br>
	 * @return a Vector of all edge names, 
	 */	
	public Vector<String> getEdgeNames() {
		Vector<String> ret = new Vector<String>();
		Iterator<MEdge> n = mEdges.iterator();
		while (n.hasNext()) {
			MEdge actual = n.next();
			if ((actual.index + 1) > ret.size()) {
				ret.setSize(actual.index + 1);
			}
			if ((actual.StartIndex==0)||(actual.EndIndex==0))
			{
				//temporäre Kante
			}
			else
			{
				ret.set(actual.index, actual.name);
			}
		}
		return ret;
	}
	/**
	 * Returns the number of edges between two given nodes. For the non-multiple case 0 means no edge 1 means an edge exists
	 *
	 * @param start start node index
	 * @param ende end node index
	 * @return
	 */
	public int EdgesBetween(int start, int ende)
	{
		int count = 0;
		EdgeLock.lock();
		try{
				Iterator<MEdge> n = mEdges.iterator();
				while (n.hasNext())
				{
					MEdge e = n.next();
					if ( ( e.StartIndex==start ) && (e.EndIndex==ende) )
						count ++; //count this edge because it is from start to end
					else if ( ( !directed ) && ( e.StartIndex==ende ) && ( e.EndIndex==start ) )
						count ++; //count this edge because in the nondirected case this is also from start to end
				}
			} finally {EdgeLock.unlock();}
		return count;
	}
	/**
	 * Get the indices of Edges between these two nodes
	 * @param start start node definnied by index
	 * @param ende end node
	 * @return
	 */
	public Vector<Integer> getEdgeIndices(int start, int ende)
	{
		Vector<Integer> liste = new Vector<Integer>();
		EdgeLock.lock();
		try{
				Iterator<MEdge> n = mEdges.iterator();
				while (n.hasNext())
				{
					MEdge e = n.next();
					if ( ( e.StartIndex==start ) && (e.EndIndex==ende) )
					{
						liste.add(e.index);
					}
					//return e.index;
					else if ( ( !directed ) && ( e.StartIndex==ende ) && ( e.EndIndex==start ) )
					{
						liste.add(e.index);
					}
					//return e.index;
				}
			} finally {EdgeLock.unlock();}
		return liste;
	}
	/**
	 * Get the number of edges in the mgraph
	 * @return number of edges
	 */	
	public int EdgeCount()
	{
		return mEdges.size();
	}
	/**
	 * Get a new Iterator for the edges. Attention: Because this stuff is threadsafe and is used in many threads the edges might change
	 */
	public Iterator<MEdge> getEdgeIterator()
	{
		return mEdges.iterator();
	}
	/*
	 * Untergraphenmethoden
	 */
	/**
	 * Add a new subgraph. if the index is already in use, nothing happens
	 * @param s Mathematical Subgraph, which should be added, a clone of the parameter is added
	 */
	public void addSubgraph(MSubgraph s)
	{
		
		if (getSubgraph(s.getIndex())==null)
		{
			mSubgraphs.add(s.clone());
			setChanged();
			notifyObservers(new GraphMessage(GraphMessage.SUBGRAPH,s.getIndex(),GraphMessage.ADDITION,GraphMessage.SUBGRAPH));	

		}
	}
	/**
	 * Remove a subgraph from the graph. If it does not exist, nothing happens.
	 * @param index subgraph given by id, that should be removed
	 * @return true if a subgraph was removed
	 */
	public boolean removeSubgraph(int index)
	{
		MSubgraph toDelete = getSubgraph(index);
		if (toDelete!=null)
		{
			mSubgraphs.remove(toDelete);
			setChanged();
			notifyObservers(new GraphMessage(GraphMessage.SUBGRAPH,index,GraphMessage.REMOVAL,GraphMessage.ALL_ELEMENTS));	
			notifyObservers("S"+index);
			return true;
		}
		return false;
	}
	/**
	 * Get the Subgraph specified by the index. If ist does not exists, the Method returns null
	 * @param index Index of the Subgraph
	 * @return the subgraph with the index, if exists, else null
	 */
	public MSubgraph getSubgraph(int index)
	{
		Iterator<MSubgraph> iter = mSubgraphs.iterator();
		while (iter.hasNext())
		{
			MSubgraph actual = iter.next();
			if (actual.getIndex()==index)
			{
				return actual;
			}
		}
		return null;
	}
	/**
	 * Get a free subgraph index
	 * @return max_subgraph_index + 1
	 */
	public int getNextSubgraphIndex() {
		int index = 1;
		Iterator<MSubgraph> n = mSubgraphs.iterator();
		while (n.hasNext()) {
			MSubgraph temp = n.next();
			if (temp.getIndex() >= index) // index vergeben
			{
				index = temp.getIndex() + 1;
			}
		}
		return index;
	}
	/**
	 * Add a Node to a Subgraph
	 * If both node and subgraph exist
	 * If the node is already in the subgraph, no notification is pushed
	 * @param nodeindex
	 * @param subgraphindex
	 */
	public void addNodetoSubgraph(int nodeindex, int subgraphindex)
	{
		if (getNode(nodeindex)==null)
			return;
		MSubgraph s = getSubgraph(subgraphindex);
		if (s==null)
			return;
		if (!s.containsNode(nodeindex)) //Change if it is not in the subgraph yet
		{ 
			s.addNode(nodeindex);
			setChanged();
			notifyObservers(new GraphMessage(GraphMessage.SUBGRAPH,subgraphindex,GraphMessage.UPDATE,GraphMessage.SUBGRAPH|GraphMessage.NODE));	
		}
	}
	/**
	 * Removes a node from a subgraph. If there was a change in the subgraph (both node an subgraph exist and the node was in the subgraph) the return value is true, else false
	 * @param nodeindex node that should be removed
	 * @param subgraphindex index of subgraph where the node should be removed
	 */
	public void removeNodefromSubgraph(int nodeindex, int subgraphindex)
	{
		MSubgraph s = getSubgraph(subgraphindex);
		if ((getNode(nodeindex)==null)||(s==null))
			return;
		if (s.containsNode(nodeindex))
		{
			s.removeNode(nodeindex);
			setChanged();
			notifyObservers(new GraphMessage(GraphMessage.SUBGRAPH,subgraphindex,GraphMessage.UPDATE,GraphMessage.SUBGRAPH|GraphMessage.NODE));	
		}
	}
	/**
	 * Add an edge to a subgraph, if both edge and subgraph exist. If they don't nothing happens
	 * @param edgeindex edge index that should be added
	 * @param subgraphindex subgraph index where the edge should be added
	 */
	public void addEdgetoSubgraph(int edgeindex, int subgraphindex)
	{
		MEdge e = getEdge(edgeindex);
		MSubgraph s = getSubgraph(subgraphindex);
		if ((e==null)||(s==null))
			return;
		if (!s.containsEdge(edgeindex))
		{
			s.addEdge(edgeindex);
			setChanged();
			notifyObservers(new GraphMessage(GraphMessage.SUBGRAPH,subgraphindex,GraphMessage.UPDATE,GraphMessage.SUBGRAPH|GraphMessage.EDGE));	
		}
	}
	/**
	 * Removes an edge from a subgraph, if both exist. If a change is done (edge is also contained in the subgraph). 
	 * If an edge is removed, so there was really a change, it returs true
	 *
	 * @param edgeindex Edge to be removed from
	 * @param subgraphindex subgraph with this index
	 *
	 * @return true if both edge and subgraph exist and the edge was in the subgraph, so it was removed
	 */
	public void removeEdgefromSubgraph(int edgeindex, int subgraphindex)
	{
		MEdge e = getEdge(edgeindex);
		MSubgraph s = getSubgraph(subgraphindex);
		if ((e==null)||(s==null))
			return;
		if (s.containsEdge(edgeindex))
		{
			s.removeEdge(edgeindex);
			setChanged();
			notifyObservers(new GraphMessage(GraphMessage.SUBGRAPH,subgraphindex,GraphMessage.UPDATE,GraphMessage.SUBGRAPH|GraphMessage.EDGE));	
		}
	}
	/**
	 * get a new Subgraph Iterator.
	 * @return
	 */
	public Iterator<MSubgraph> getSubgraphIterator()
	{
		return mSubgraphs.iterator();
	}
	/**
	 * Get the number of subgraphs in the mgraph
	 * @return the number of subgraphs in the mgraph
	 */
	public int SubgraphCount() {
		return mSubgraphs.size();
	}
	/**
	 * get a list of the subgraphs names in a vector, where each subgraph name is stored at it's index
	 * every other component of the vector is null
	 * @return a Vector of all subgraphs names, 
	 */	
	public Vector<String> getSubgraphNames() {
		Vector<String> ret = new Vector<String>();
		Iterator<MSubgraph> s = mSubgraphs.iterator();
		while (s.hasNext()) {
			MSubgraph actual = s.next();
			if ((actual.getIndex() + 1) > ret.size()) {
				ret.setSize(actual.getIndex() + 1);
			}
			ret.set(actual.getIndex(), getSubgraph(actual.getIndex()).getName());
		}
		return ret;
	}
}