package at.ac.ist.fiji.cellcounter;
/*
 * CellCntrImageCanvas.java Created on November 22, 2005, 5:58 PM
 */

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.ListIterator;
import java.util.Vector;

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

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.process.ImageProcessor;

/**
 * @author Kurt De Vos
 */
public class CellCntrImageCanvas extends ImageCanvas {

    private static final int POINT_SIZE = 8;

    private Vector typeVector;

    private CellCntrMarkerVector currentMarkerVector;

    private final CellCounter cc;

    private final ImagePlus img;

    private boolean delmode = false;

    private boolean showNumbers = true;

    private boolean showAll = false;

    private final Font font = new Font("SansSerif", Font.PLAIN, 10);

    boolean mousePressed = false;

    private Rectangle srcRect = new Rectangle(0, 0, 0, 0);

    /** Creates a new instance of CellCntrImageCanvas */
    public CellCntrImageCanvas(ImagePlus img, Vector typeVector, CellCounter cc, Vector displayList) {
        super(img);
        this.img = img;
        this.typeVector = typeVector;
        this.cc = cc;
        if (displayList != null) {
            this.setDisplayList(displayList);
        }
    }

    public CellCntrMarkerVector getCurrentMarkerVector() {
        return this.currentMarkerVector;
    }

    public Vector getTypeVector() {
        return this.typeVector;
    }

    public ImagePlus imageWithMarkers() {
        Image image = this.createImage(this.img.getWidth(), this.img.getHeight());
        Graphics gr = image.getGraphics();

        double xM = 0;
        double yM = 0;

        try {
            if (this.imageUpdated) {
                this.imageUpdated = false;
                this.img.updateImage();
            }
            Image image2 = this.img.getImage();
            if (image != null) {
                gr.drawImage(image2, 0, 0, this.img.getWidth(), this.img.getHeight(), null);
            }
        } catch (OutOfMemoryError e) {
            IJ.outOfMemory("Paint " + e.getMessage());
        }

        Graphics2D g2r = (Graphics2D) gr;
        g2r.setStroke(new BasicStroke(1f));

        ListIterator it = this.typeVector.listIterator();
        while (it.hasNext()) {
            CellCntrMarkerVector mv = (CellCntrMarkerVector) it.next();
            int typeID = mv.getType();
            g2r.setColor(mv.getColor());
            ListIterator mit = mv.listIterator();
            while (mit.hasNext()) {
                CellCntrMarker m = (CellCntrMarker) mit.next();
                if (m.getZ() == this.img.getCurrentSlice()) {
                    xM = m.getX();
                    yM = m.getY();
                    g2r.fillOval((int) xM - 2, (int) yM - 2, 4, 4);
                    if (this.showNumbers) {
                        g2r.drawString(Integer.toString(typeID), (int) xM + 3, (int) yM - 3);
                    }
                }
            }
        }

        Vector displayList = getDisplayList();
        if (displayList != null && displayList.size() == 1) {
            Roi roi = (Roi) displayList.elementAt(0);
            if (roi.getType() == Roi.COMPOSITE) {
                roi.draw(gr);
            }
        }

        return new ImagePlus("Markers_" + this.img.getTitle(), image);
    }

    public boolean isDelmode() {
        return this.delmode;
    }

    public boolean isShowNumbers() {
        return this.showNumbers;
    }

    public void measure() {
        IJ.setColumnHeadings("Type\tSlice\tX\tY\tValue");
        for (int i = 1; i <= this.img.getStackSize(); i++) {
            this.img.setSlice(i);
            ImageProcessor ip = this.img.getProcessor();

            ListIterator it = this.typeVector.listIterator();
            while (it.hasNext()) {
                CellCntrMarkerVector mv = (CellCntrMarkerVector) it.next();
                int typeID = mv.getType();
                ListIterator mit = mv.listIterator();
                while (mit.hasNext()) {
                    CellCntrMarker m = (CellCntrMarker) mit.next();
                    if (m.getZ() == i) {
                        int xM = m.getX();
                        int yM = m.getY();
                        int zM = m.getZ();
                        double value = ip.getPixelValue(xM, yM);
                        IJ.write(typeID + "\t" + zM + "\t" + xM + "\t" + yM + "\t" + value);
                    }
                }
            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        int x = super.offScreenX(e.getX());
        int y = super.offScreenY(e.getY());
        this.cc.clickWithShift(x, y);
        super.mouseDragged(e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        super.mouseEntered(e);
        if (!IJ.spaceBarDown() | Toolbar.getToolId() != Toolbar.MAGNIFIER | Toolbar.getToolId() != Toolbar.HAND) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        super.mouseExited(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (this.mousePressed) {
            int x = super.offScreenX(e.getX());
            int y = super.offScreenY(e.getY());
            if (IJ.shiftKeyDown() && IJ.controlKeyDown()) {
                this.cc.clickWithShiftCtrl(x, y);
            } else if (IJ.shiftKeyDown()) {
                this.cc.clickWithShift(x, y);
            } else if (IJ.controlKeyDown()) {
                this.cc.clickWithCtrl(x, y);
            }
        }
        super.mouseMoved(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (IJ.spaceBarDown() || Toolbar.getToolId() == Toolbar.MAGNIFIER || Toolbar.getToolId() == Toolbar.HAND) {
            super.mousePressed(e);
            return;
        }

        if (this.currentMarkerVector == null) {
            IJ.error("Select a counter type first!");
            return;
        }
        this.mousePressed = true;
        int x = super.offScreenX(e.getX());
        int y = super.offScreenY(e.getY());
        if (IJ.shiftKeyDown() && IJ.controlKeyDown()) {
            this.cc.clickWithShiftCtrl(x, y);
        } else if (IJ.shiftKeyDown()) {
            this.cc.clickWithShift(x, y);
        } else if (IJ.controlKeyDown()) {
            this.cc.clickWithCtrl(x, y);
        } else if (this.cc.newGermBandActive) {
            this.cc.defineNewGermBand(x, y);
        } else if (!this.delmode) {
            CellCntrMarker m = new CellCntrMarker(x, y, this.img.getCurrentSlice(), this.img.getZ(), null);
            this.currentMarkerVector.addMarker(m);
            if (this.currentMarkerVector.getType() == 7) {
                this.cc.tailRetractionMarker(this.currentMarkerVector);
            }
        } else {
            CellCntrMarker m = this.currentMarkerVector.getMarkerFromPosition(new Point(x, y), this.img.getCurrentSlice());
            this.currentMarkerVector.remove(m);
        }
        repaint();
        this.cc.populateTxtFields();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        this.mousePressed = false;
        super.mouseReleased(e);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        this.srcRect = getSrcRect();
        Roi roi = this.img.getRoi();
        double xM = 0;
        double yM = 0;

        /*
         * double magnification = super.getMagnification(); try { if
         * (imageUpdated) { imageUpdated = false; img.updateImage(); } Image
         * image = img.getImage(); if (image!=null) g.drawImage(image, 0, 0,
         * (int)(srcRect.width*magnification),
         * (int)(srcRect.height*magnification), srcRect.x, srcRect.y,
         * srcRect.x+srcRect.width, srcRect.y+srcRect.height, null); if (roi !=
         * null) roi.draw(g); } catch(OutOfMemoryError e) {
         * IJ.outOfMemory("Paint "+e.getMessage()); }
         */

        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(1f));
        g2.setFont(this.font);
        ListIterator<CellCntrMarkerVector> it = this.typeVector.listIterator();
        while (it.hasNext()) {
            CellCntrMarkerVector mv = it.next();
            int typeID = mv.getType();
            g2.setColor(mv.getColor());
            try {
                ListIterator<CellCntrMarker> mit = new ArrayList<>(mv).listIterator();
                while (mit.hasNext()) {
                    CellCntrMarker m = mit.next();
                    boolean sameSlice = m.getZ() == this.img.getCurrentSlice();
                    if (sameSlice || this.showAll) {
                        xM = (m.getX() - this.srcRect.x) * this.magnification;
                        yM = (m.getY() - this.srcRect.y) * this.magnification;
                        if (sameSlice) {
                            g2.fillOval((int) xM - 2, (int) yM - 2, POINT_SIZE, POINT_SIZE);
                        } else {
                            g2.drawOval((int) xM - 2, (int) yM - 2, POINT_SIZE, POINT_SIZE);
                        }
                        if (this.showNumbers) {
                            g2.drawString(Integer.toString(typeID), (int) xM + 3, (int) yM - 3);
                        }
                    }
                }
            } catch (ConcurrentModificationException ccm) {
                // ignore this a repaint will occure
            }
        }
    }

    public void removeLastMarker() {
        this.currentMarkerVector.removeLastMarker();
        repaint();
        this.cc.populateTxtFields();
    }

    public void setCurrentMarkerVector(CellCntrMarkerVector currentMarkerVector) {
        this.currentMarkerVector = currentMarkerVector;
    }

    public void setDelmode(boolean delmode) {
        this.delmode = delmode;
    }

    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
    }

    public void setShowNumbers(boolean showNumbers) {
        this.showNumbers = showNumbers;
    }

    public void setTypeVector(Vector typeVector) {
        this.typeVector = typeVector;
    }

}
