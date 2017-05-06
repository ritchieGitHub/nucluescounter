package at.ac.ist.fiji.cellcounter;
/*
 * Marker.java
 *
 * Created on December 13, 2005, 8:41 AM
 *
 */

/*
 *
 * @author Kurt De Vos � 2005
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. 
 *
 */

import ij.gui.Roi;

/**
 *
 * @author Kurt De Vos
 */
public class CellCntrMarker {
	private int x;
	private int y;
	private int z;
	private Roi roi;

	/** Creates a new instance of Marker */
	public CellCntrMarker() {
	}

	public CellCntrMarker(int x, int y, int z, Roi roi) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.roi = roi;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getZ() {
		return z;
	}

	public void setZ(int z) {
		this.z = z;
	}

}
