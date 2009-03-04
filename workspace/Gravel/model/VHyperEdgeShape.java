package model;

import java.awt.Point;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.Iterator;
import java.util.Vector;

import javax.vecmath.Point3d;

/**
 * This Class represents the Shape of an individual VHyperEdge
 * Its shape is based on NURBS and a minimal distance it should have from each node
 * This Minimum Distance is used to evaluate the validity of the Shape
 * @see VHyperEdge
 * 
 * Other Methods are
 * - Min and Max of the Bounding Box of the ControlPolygon
 * - Addition (Refinement of Knots)
 *
 * - TODO Constructor with Interpolation-Points
 * - TODO Movement of an IP (possible ?)
 * - TODO Get&Set single CPs (when they're moved)
 * - TODO (needed?) get&set knots &weights
 * - TODO In/Decrease Degree of the polynonials 
 * 
 * @author Ronny Bergmann
 *
 */
public class VHyperEdgeShape {

	private Vector<Double> t; //knots
	private Vector<Double> w; //weights
	public Vector<Point2D> P; //ControlPoints
	private Vector<Point3d> Pw; //b in homogeneous coordinates multiplied by weight

	private int minDist;
	private int m,n,d; //n is the number of CP, d the Order of the polynom-splines, m=n+d+1

	/**
	 * Create an empty shape so nothing ever happens but its at least not null
	 */
	public VHyperEdgeShape()
	{
		t = new Vector<Double>();
		w = new Vector<Double>();
		P = new Vector<Point2D>();
		n=0; m=0; d=0;
		Pw = new Vector<Point3d>();
	}
	/**
	 * Init an HyperEdgeShape with
	 * @param knots nots of the NURBS
	 * @param cpoints Controlpoints of the NURBS
	 * @param weights weights of the CP 
	 * @param dist minimal distance the curve should have from each node (whose are not saved here)
	 */
	public VHyperEdgeShape(Vector<Double> knots, Vector<Point2D> cpoints, Vector<Double> weights, int dist)//, int degree)
	{
		minDist = dist;
		setCurveTo(knots,cpoints,weights);
	}
	/**
	 * Set the Curve to another NURBS
	 * @param knots
	 * @param cpoints
	 * @param weights
	 */
	public void setCurveTo(Vector<Double> knots, Vector<Point2D> cpoints, Vector<Double> weights)
	{
		t = knots;
		P = cpoints;
		w = weights;
		n = cpoints.size()-1;
		m = t.size()-1;
		d = t.size()-P.size()-1;
		InitHomogeneous();
	}
	private VHyperEdgeShape(Vector<Double> knots, Vector<Point3d> pPw, int dist)
	{
		t = new Vector<Double>();
		w = new Vector<Double>();
		P = new Vector<Point2D>();
		n=0; m=0; d=0;
		Pw = new Vector<Point3d>();
		P.setSize(pPw.size()); 
		w.setSize(pPw.size());
		for (int i=0; i<pPw.size(); i++)
		{
			double stw = pPw.get(i).z;
			if (stw==0)
				P.set(i,new Point2D.Double(pPw.get(i).x,pPw.get(i).y));
			else
				P.set(i,new Point2D.Double(pPw.get(i).x/pPw.get(i).z,pPw.get(i).y/pPw.get(i).z));
			w.add(i,stw);
		}
		minDist = dist;
		t = knots;
		n = P.size()-1;
		m = t.size()-1;
		d = t.size()-P.size()-1;
		InitHomogeneous();		
	}
	/**
	 * Initialization of the internal homogeneous Vector
	 * Should be called everytime either the b or w vector are completly exchanged
	 */
	private void InitHomogeneous()
	{
		Pw = new Vector<Point3d>();
		Iterator<Point2D> ib =  P.iterator();
		while (ib.hasNext()) //Modify to be 3D Coordinates (homogeneous 2D)
		{
			Point2D p = ib.next();
			double weight = w.get(P.indexOf(p));
			Point3d newp = new Point3d(p.getX(),p.getY(),weight);
			newp.set(newp.x*weight, newp.y*weight, weight);
			Pw.add(newp);
		}		
	}
	/**
	 * Get Maximum (bottom right edge) of the CP bunding box
	 */
	public Point2D getMax()
	{
		double x = Double.MIN_VALUE;
		double y = Double.MIN_VALUE;
		Iterator<Point2D> bi = P.iterator();
		while (bi.hasNext())
		{
			Point2D p = bi.next();
			if (p.getX() > x)
				x = p.getX();
			if (p.getY() > y)
				y = p.getY();
		}
		return new Point2D.Double(x,y);
	}
	/**
	 * Get Minimum (top left edge) of the CP bunding box
	 */
	public Point2D getMin()
	{
		double x = Double.MAX_VALUE;
		double y = Double.MAX_VALUE;
		Iterator<Point2D> bi = P.iterator();
		while (bi.hasNext())
		{
			Point2D p = bi.next();
			if (p.getX() < x)
				x = p.getX();
			if (p.getY() < y)
				y = p.getY();
		}
		return new Point2D.Double(x,y);
	}
	/**
	 * Scale all Controlpoints by factor s, of you want to resize a shape
	 * make shure to translate its middle to 0,0 before and back afterwards
	 * @param s
	 */
	public void scale(double s)
	{
		Iterator<Point2D> bi = P.iterator();
		while (bi.hasNext())
		{
			Point2D p = bi.next();
			p.setLocation(p.getX()*s,p.getY()*s);
		}
		//recalculate Homogeneous
		InitHomogeneous();
		
	}
	public void translate(double x, double y)
	{
		Vector<Point2D> Q = new Vector<Point2D>();
		Iterator<Point2D> bi = P.iterator();
		while (bi.hasNext())
		{
			Point2D p = bi.next();
			Q.add(new Point2D.Double(p.getX()+x,p.getY()+y));
		}
		this.setCurveTo(t,Q,w);
	}
	/**
	 * Get the Curve as a piecewise approximated linear Java Path
	 * @param stepsize Size in the Intervall two points on the path differ
	 * TODO: Vary that value to an maximum distance thwo points on the path should have (as Bezier-Paths do)
	 * @return
	 */
	public GeneralPath getCurve(double stepsize) //Adapt to a length on the curve?
	{
		//Intervallborders
		double first = t.firstElement();
		double last = t.lastElement();
		double actual = first;
		GeneralPath path = new GeneralPath();
		Point2D.Double f = NURBSCurveAt(first);
		path.moveTo((new Double(f.x)).floatValue(), (new Double(f.y)).floatValue());
		actual+=stepsize;
		while (actual<=last)
		{
			f = NURBSCurveAt(actual);
			path.lineTo((new Double(f.x)).floatValue(), (new Double(f.y)).floatValue());
			actual+=stepsize;
		}
	//	path.closePath();
		
		return path;
	}
	/**
	 * Find the interval u \in [t.get(j),t.get(j+1)] and return the index j
	 * 
	 * because the first and last d+1 values of t are assumed equal, the 
	 * @param u
	 * @return
	 */
	private int findSpan(double u)
	{
		if (u==t.lastElement())
			return t.indexOf(t.lastElement())-1; //first value of t equal to t.get(m)==t.lastElement - which is m-d		
		//Binary Search for the intervall
		int low = d; //because the first d+1 are equal too
		int high = m-d; //see above
		int mid = Math.round((low+high)/2);
		while ((u<t.get(mid)) || (u>=t.get(mid+1)))
		{ 
			if (u < t.get(mid))
					high = mid;
			else
				low = mid;
			mid = Math.round((low+high)/2);
		} //get the first t AFTER u in Variable j
		return mid;
	}
	/**
	 * Private Method to evaluate the Curve at given point u \in [t_0,t_m]
	 * @param u
	 * @return
	 */
	public Point2D.Double NURBSCurveAt(double u)
	{	
		int j = findSpan(u);			
		Point3d erg = NURBS(u,j,d); //Result in homogeneous Values on Our Points		
		if (erg.z==0) //
			return new Point2D.Double(erg.x,erg.y);
		else
			return new Point2D.Double(erg.x/erg.z,erg.y/erg.z);
		
	}
	public Point2D DerivateCurveAt(int degree, double u)
	{
		if (degree==0)
			return NURBSCurveAt(u);
		Vector<Point3d> DerivatesBSpline = new Vector<Point3d>();
		DerivatesBSpline.setSize(degree+1);
		int actdeg = 1;
		while (actdeg<=degree) //Generate all Values of lower derivates at Point u in homogeneous BSpline-Points
		{
			Vector<Point3d> theirCP = CPofDerivate(actdeg);
			Vector<Double> theirt = new Vector<Double>();
			for (int i=actdeg; i<=m-actdeg; i++)
				theirt.add(i-actdeg,t.get(i));
			VHyperEdgeShape theirCurve = new VHyperEdgeShape(theirt,theirCP,0);
			Point3d derivp= theirCurve.NURBS(u,theirCurve.findSpan(u),theirCurve.d);
			DerivatesBSpline.set(actdeg,derivp); 
			actdeg++;
		}
		Vector<Point2D> DerivatesNURBS = new Vector<Point2D>();
		DerivatesNURBS.setSize(degree);
		DerivatesNURBS.set(0,NURBSCurveAt(u));
		for (int k=1; k<=degree; k++)
		{ //Calculate kth Derivate
			Point2D.Double thisdeg = new Point2D.Double(DerivatesBSpline.get(k).x,DerivatesBSpline.get(k).y);
			double denominator = DerivatesBSpline.get(k).z;
			for (int i=1; i<=k; i++)
			{
				double factor = binomial(k,i)*DerivatesBSpline.get(i).z;
				Point2D prev = (Point2D) DerivatesNURBS.get(k-i).clone();
				thisdeg.x = thisdeg.x - prev.getX()*factor;
				thisdeg.y = thisdeg.y - prev.getY()*factor;
			}
			if (denominator!=0.0)
			{
				thisdeg.x = thisdeg.x/denominator;
				thisdeg.y = thisdeg.y/denominator;
			}
			DerivatesNURBS.add(k, new Point2D.Double(thisdeg.x,thisdeg.y));
		}
		return DerivatesNURBS.elementAt(degree);
	}

	/**
	 * Calulation of Alpha, refer to deBoer-Algorithm
	 * @param u
	 * @param i
	 * @param j
	 * @return
	 */
	private double alpha(double u,int i, int j)
	{
		if ((u==t.get(i)&&(t.get(i+d-j+1)==t.get(i))))
			return 0;
		return (u-t.get(i))/(t.get(i+d-j+1)-t.get(i));
	}
	/**
	 * Calculate the Value of the NURBSCurve in homogeneous Coordinates iterative
	 * 
	 * This method works for 2d homogeneous or 3d Stuff.
	 *
	 * @param u Point u \in [0,1], which result we want
	 * @param i Number of the Basis function of specific
	 * @param j Degree j
	 * @return a 3d-Value of the Point in the Curve.
	 */
	private Point3d NURBS(double u, int i, int j)
	{
		Vector<Point3d> fixedj = new Vector<Point3d>();
		fixedj.setSize(j+1); //for values 0,...,j
		//Init with the Points
		for (int l=i; l>=i-j; l--) //Beginning with i,0 up to i-j,0
		{
			fixedj.set(l-i+j,Pw.get(l));
		}
		
		for (int k=1; k<=j; k++) //Compute higher and hihger values of j
		{
			for (int l=i; l>=i-j+k; l--) //Stop each iteration one earlier
			{
				Point3d bimjm = fixedj.get(l-i+j-1);//b_i-1^j-1
				double alpha = alpha(u,l,k);
				Point3d bijm = fixedj.get(l-i+j);
				double x = (1-alpha)*bimjm.x + alpha*bijm.x;
				double y = (1-alpha)*bimjm.y + alpha*bijm.y;
				double z = (1-alpha)*bimjm.z + alpha*bijm.z;
				fixedj.set(l-i+j,new Point3d(x,y,z));
				//System.err.println("Computing ("+l+","+k+") :"+fixedj.get(l-i+j));
				//saving in "+(l-i+j)+" based on pervious values in "+(l-i+j-1)+" and "+(l-i+j)+".");
			}
		}
		return fixedj.get(j);
	}
	/**
	 * Compares this Curve to another (minDist does not matter)
	 * if alle values of t,b,w are equal it returns true, else false
	 * 
	 * TODO: Check whether there's a way to check for equality if these values are different
	 * @param s another Shape
	 * @return true if Shape s is equal to this else false
	 */
	public boolean CurveEquals(VHyperEdgeShape s)
	{
		if ((s.P.size()!=P.size())||(t.size()!=s.t.size()))
			return false;
		Iterator<Point2D> bi = P.iterator();
		while (bi.hasNext())
		{
			Point2D p = bi.next();
			if (s.P.get(P.indexOf(p)).distance(p)!=0.0d)
				return false;
		}
		Iterator<Double> ti = t.iterator();
		while (ti.hasNext())
		{
			Double v = ti.next();
			if (s.t.get(t.indexOf(v)).compareTo(v)==0) //Equal
				return false;
		}
		Iterator<Double> wi = w.iterator();
		while (wi.hasNext())
		{
			Double v = wi.next();
			if (s.w.get(w.indexOf(v)).compareTo(v)==0) //Equal
				return false;
		}
		return true;
	}

	public Point2D getNearestCP(Point m) {
		double mindist = Double.MAX_VALUE;
		Point2D result = null;
		Iterator<Point2D> bi = P.iterator();
		while (bi.hasNext())
		{
			Point2D p = bi.next();
			double dist = p.distance(m);
			if (dist < mindist)
			{
				mindist = dist;
				result = p;
			}
		}
		return result;
	}
	public boolean isPointOnCurve(Point2D x, double variance)
	{
		return false;
	}
	/**
	 * Refine the Curve to add some new knots contained in X from wich each is between t[0] and t[m]
	 * @param X
	 */
	public void RefineKnots(Vector<Double> X)
	{
		//Compare The NURBS Book A5.4
		int a = findSpan(X.firstElement()), b=findSpan(X.lastElement())+1;
		Vector<Point3d> newPw;
		newPw = new Vector<Point3d>();
		newPw.setSize(Pw.size()+X.size());
		Vector<Double> newt = new Vector<Double>();
		newt.setSize(t.size()+X.size());
		for (int j=0; j<=a-d; j++)//Copy the first not changed values of the CPs
			newPw.set(j, Pw.get(j));
		for (int j=b-1; j<=n; j++)//Copy the last not changed values of the CPs
			newPw.set(j+X.size(), Pw.get(j));
		for (int j=0; j<=a; j++)//Copy the first not changed values of t
			newt.set(j, t.get(j));
		for (int j=b+d; j<=m; j++)//Copy the last not changed values of t
			newt.set(j+X.size(), t.get(j));
		
		int i=b+d-1; //Last Value that's new in t
		int k=b+d+X.size()-1; //Last Value that's new in Pw
		for (int j=X.size()-1; j>=0; j--) //Insert new knots backwards beginning at X.lastElement
		{ 
			while (X.get(j) <= t.get(i) && i > a) //These Values are not affected by Insertion of actual Not, copy them
			{
				newPw.set(k-d-1, (Point3d) Pw.get(i-d-1).clone());
				newt.set(k, t.get(i));
				k--;i--;
			}
			newPw.set(k-d-1, (Point3d) newPw.get(k-d).clone());
			for (int l=1; l<=d; l++)
			{
				int actualindex = k-d+l;
				double alpha = newt.get(k+l)-X.get(j);
				if (Math.abs(alpha) == 0.0d)
					newPw.set(actualindex-1, (Point3d) newPw.get(actualindex).clone());
				else
				{
					alpha = alpha/(newt.get(k+l)-t.get(i-d+l));
					Point3d p1 = (Point3d) newPw.get(actualindex-1).clone();
					p1.scale(alpha);
					Point3d p2 = (Point3d) newPw.get(actualindex).clone();
					p2.scale(1.0d - alpha);
					p1.add(p2);
					newPw.set(actualindex-1,p1);
				}
			} //All Points recomputed for this insertion
			newt.set(k, X.get(j));
			k--;
		}
		//Recompute Points & weights
		P = new Vector<Point2D>(); 
		w = new Vector<Double>();
		Iterator<Point3d> Pwi = newPw.iterator();
		while (Pwi.hasNext())
		{
			Point3d p1 = Pwi.next();
			if (p1.z==0)
				P.add(new Point2D.Double(p1.x,p1.y));
			else
				P.add(new Point2D.Double(p1.x/p1.z, p1.y/p1.z));
			w.add(p1.z);
		}
		t = newt;
		n = P.size()-1;
		m = t.size()-1;
		d = t.size()-P.size()-1;
		InitHomogeneous();
	}

	@SuppressWarnings("unchecked")
	private Vector<Point3d> CPofDerivate(int degree)
	{
		Vector<Point3d> result = new Vector<Point3d>();
		if (degree==0)
		{
			for (int i=0; i<Pw.size(); i++)
				result.add((Point3d) Pw.get(i).clone());
			return result;
		}
		Vector<Point3d> degm1 = CPofDerivate(degree-1);
		//Compute from those the actually wanted ones
		for (int i=0; i<degm1.size()-1; i++) //one less
		{
			double factor = (d-degree+1)/(t.get(i+d+1)-t.get(i+degree));
			Point3d next = (Point3d) degm1.get(i+1).clone();
			next.sub(degm1.get(i));
			next.scale(factor);
			result.add(next);
		}
		return result;		
	}
	/**
	 * Projects the point d to a point, whose distance is minimal to d and on the curve
	 * @param d
	 * @return
	 */
	public Point2D ProjectionPoint(Point2D d)
	{
		//TODO: Set the value of the intervalls of u heuristically by length of the line
		double eqdist = .0002; //Find a nice Start-value for u
		double u = t.firstElement(),u0 = t.firstElement();
		double mindist = Double.MAX_VALUE;
		while (u<=t.lastElement())
		{
			Point2D p = NURBSCurveAt(u);
			double thisdist = p.distance(d);
			if (thisdist<mindist)
			{
				u0=u;
				mindist=thisdist;
			}
			u+=eqdist;
		}
		boolean running = true;
		Point2D.Double Value = (Point2D.Double) NURBSCurveAt(u0);
		Point2D.Double firstDeriv = (Point2D.Double) DerivateCurveAt(1,u0);
		Point2D.Double secondDeriv = (Point2D.Double) DerivateCurveAt(2,u0);
		Point2D.Double diff = new Point2D.Double(Value.x-d.getX(),Value.y-d.getY());
		double ulast = Double.MAX_VALUE;
		u=u0;
		int iterations=0;
		while (running)
		{
			iterations++;
			double nominator = firstDeriv.x*diff.x + firstDeriv.y*diff.y;
			double denominator = secondDeriv.x*diff.y + secondDeriv.y*diff.y + firstDeriv.x*firstDeriv.x + firstDeriv.y*firstDeriv.y;
			double unext = u - nominator/denominator;
			if (unext > t.lastElement()) //Out of Range
			{
				if (P.lastElement().distance(P.firstElement())==0) //closed
				{
					while (unext > t.lastElement())
						unext = t.firstElement() + (unext-t.lastElement());
				}
				else //open
					unext = t.lastElement();
			}
			if (unext < t.firstElement()) //Out of Range
			{
				if (P.lastElement().distance(P.firstElement())==0) //closed
				{
					while (unext < t.firstElement())
						unext = t.lastElement() - (t.firstElement()-unext);
				}
				else //open
					unext = t.firstElement();
			}
			if (ulast==unext)
			{
				running=false; //TwoPointCircle
			}
			Value = (Point2D.Double) NURBSCurveAt(unext);
			firstDeriv = (Point2D.Double) DerivateCurveAt(1,unext);
			secondDeriv = (Point2D.Double) DerivateCurveAt(2,unext);
			diff = new Point2D.Double(Value.x-d.getX(),Value.y-d.getY());
			double coincidence = Math.sqrt(diff.x*diff.x + diff.y*diff.y);
			double movement = Math.abs(unext-u)*Math.sqrt(firstDeriv.x*firstDeriv.x + firstDeriv.y*firstDeriv.y);
			ulast=u;
			u=unext;			
			//System.err.println(coincidence+" and "+movement);
			if ((coincidence <= 0.002d)||(movement<=0.002d))
				running=false;
			if (iterations>1000) //Verhindere Schleifen
				running=false;
		}
//		System.err.print(iterations+" - ");
//		if (iterations>=1000)
//			System.err.println("Loop!");
		return NURBSCurveAt(u);
	}
	// return integer nearest to x
	long nint(double x) {
		if (x < 0.0) return (long) Math.ceil(x - 0.5);
	      return (long) Math.floor(x + 0.5);
	}

	   // return log n!
	   double logFactorial(int n) {
	      double ans = 0.0;
	      for (int i = 1; i <= n; i++)
	         ans += Math.log(i);
	      return ans;
	   }

	   // return the binomial coefficient n choose k.
	   long binomial(int n, int k) {
	      return nint(Math.exp(logFactorial(n) - logFactorial(k) - logFactorial(n-k)));
	   }
}
