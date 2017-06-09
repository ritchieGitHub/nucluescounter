package at.ac.ist.fiji.cellcounter;

/*
 * MarkerVector.java Created on December 13, 2005, 8:40 AM
 */
/*
 * @author Kurt De Vos � 2005 This program is free software; you can
 * redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation
 * (http://www.gnu.org/licenses/gpl.txt ) This program is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details. You should have received a
 * copy of the GNU General Public License along with this program; if not, write
 * to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston,
 * MA 02111-1307, USA.
 */
import java.awt.Color;
import java.awt.Point;
import java.util.ListIterator;
import java.util.Vector;

/**
 * @author Kurt De Vos
 */
public class CellCntrMarkerVector extends Vector<CellCntrMarker> {

    private int type;

    private Color color;

    /** Creates a new instance of MarkerVector */
    public CellCntrMarkerVector(int type) {
        super();
        this.type = type;
        this.color = createColor(type);
    }

    public void addMarker(CellCntrMarker marker) {
        add(marker);
    }

    private Color createColor(int typeID) {
        switch (typeID) {
            case 1:
                return Color.blue;
            case 2:
                return Color.cyan;
            case 3:
                return Color.green;
            case 4:
                return Color.magenta;
            case 5:
                return Color.orange;
            case 6:
                return Color.pink;
            case 7:
                return Color.red;
            case 8:
                return Color.yellow;
            default:
                Color c = new Color((int) (255 * Math.random()), (int) (255 * Math.random()), (int) (255 * Math.random()));
                while (c.equals(Color.blue) | c.equals(Color.cyan) | c.equals(Color.green) | c.equals(Color.magenta) | c.equals(Color.orange) | c.equals(Color.pink)
                        | c.equals(Color.red) | c.equals(Color.yellow)) {
                    c = new Color((int) (255 * Math.random()), (int) (255 * Math.random()), (int) (255 * Math.random()));
                }
                return c;
        }
    }

    public Color getColor() {
        return this.color;
    }

    public CellCntrMarker getMarker(int n) {
        return get(n);
    }

    public CellCntrMarker getMarkerFromPosition(Point p, int sliceIndex) {
        Vector v = new Vector();
        ListIterator it = this.listIterator();
        while (it.hasNext()) {
            CellCntrMarker m = (CellCntrMarker) it.next();
            if (m.getZ() == sliceIndex) {
                v.add(m);
            }
        }
        CellCntrMarker currentsmallest = (CellCntrMarker) v.get(0);
        for (int i = 1; i < v.size(); i++) {
            CellCntrMarker m2 = (CellCntrMarker) v.get(i);
            Point p1 = new Point(currentsmallest.getX(), currentsmallest.getY());
            Point p2 = new Point(m2.getX(), m2.getY());
            boolean closer = Math.abs(p1.distance(p)) > Math.abs(p2.distance(p));
            if (closer) {
                currentsmallest = m2;
            }
        }

        return currentsmallest;
    }

    public int getType() {
        return this.type;
    }

    public int getVectorIndex(CellCntrMarker marker) {
        return indexOf(marker);
    }

    public void removeLastMarker() {
        super.removeElementAt(size() - 1);
    }

    public void removeMarker(int n) {
        remove(n);
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public void setType(int type) {
        this.type = type;
    }

}
