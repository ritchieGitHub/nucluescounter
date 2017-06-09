package at.ac.ist.fiji.cellcounter;
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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EtchedBorder;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.gui.ScrollbarWithLabel;
import ij.gui.ShapeRoi;
import ij.gui.StackWindow;
import ij.io.FileInfo;
import ij.plugin.RoiEnlarger;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

public class CellCounter extends JFrame implements ActionListener, ItemListener {

    /** Show the GUI threadsafe */
    private static class GUIShower implements Runnable {

        final JFrame jFrame;

        public GUIShower(JFrame jFrame) {
            this.jFrame = jFrame;
        }

        @Override
        public void run() {
            this.jFrame.pack();
            this.jFrame.setLocation(1000, 200);
            this.jFrame.setVisible(true);
        }
    }

    private static final String ADD = "Add";

    private static final String REMOVE = "Remove";

    private static final String INITIALIZE = "Initialize";

    private static final String RESULTS = "Results";

    private static final String DELETE = "Delete";

    private static final String DELMODE = "Delete Mode";

    private static final String KEEPORIGINAL = "Keep Original";

    private static final String SHOWNUMBERS = "Show Numbers";

    private static final String SHOWALL = "Show All";

    private static final String RESET = "Reset";

    private static final String EXPORTMARKERS = "Save Markers";

    private static final String LOADMARKERS = "Load Markers";

    private static final String EXPORTIMG = "Export Image";

    private static final String MEASURE = "Measure...";

    private static final String NEW_GERM_BAND = "New germ band";

    static CellCounter instance;

    public static final int SAVE = FileDialog.SAVE, OPEN = FileDialog.LOAD;

    private static final String TAIL_RETRACT = "Tail reatr.";

    private static final String COUNTED = "Counted";

    private static final String DOUBLE = "Double";

    private static final String OUT_OF_RANGE = "Out of range";

    public static void add(int x, int y, int z, int realZ, Roi roi) {
        CellCntrMarker marker = new CellCntrMarker(x, y, z, realZ, roi);
        instance.getCurrentMarkerVector();
        ((CellCntrMarkerVector) instance.typeVector.get(0)).addMarker(marker);
        updateGui();
    }

    private static boolean areTwoMarkersSame(CellCntrMarker current, CellCntrMarker other) {
        int layerDistance = Math.abs(current.getZ() - other.getZ());
        if (layerDistance > 2 || layerDistance == 0) {
            return false;
        }
        double both = getContainedPointsCount(current.getRoi(), other.getRoi());
        if (both > 0.1) {
            double currentPoints = getContainedPointsCount(current.getRoi());
            double otherPoints = getContainedPointsCount(other.getRoi());
            if (both / currentPoints > 0.4 || both / otherPoints > 0.4) {
                System.out.println("duplicate " + //
                        current.getRoi() + "(" + current.getZ() + ")" + "\t/\t" + //
                        other.getRoi() + "(" + other.getZ() + ")");
                return true;
            }
        }
        return false;
    }

    public static void checkInRegion(Roi larger, Roi justOutOfRange) {
        instance.larger = larger;
        instance.justOutOfRange = justOutOfRange;
        if (instance.larger == null || instance.justOutOfRange == null) {
            updateGui();
            return;
        }
        CellCntrMarkerVector countedMarkers = (CellCntrMarkerVector) instance.typeVector.get(0);
        CellCntrMarkerVector outOfRangeMarkers = (CellCntrMarkerVector) instance.typeVector.get(1);
        CellCntrMarkerVector doubleMarkers = (CellCntrMarkerVector) instance.typeVector.get(2);

        HashSet<CellCntrMarker> allPoints = new HashSet<>();
        allPoints.addAll(instance.invisiblePoints);
        allPoints.addAll(countedMarkers);
        allPoints.addAll(outOfRangeMarkers);
        allPoints.addAll(doubleMarkers);

        countedMarkers.clear();
        outOfRangeMarkers.clear();
        doubleMarkers.clear();
        countedMarkers.addAll(allPoints);
        instance.invisiblePoints.clear();

        instance.invisiblePoints.addAll(instance.forcedDelete);
        countedMarkers.removeAll(instance.forcedDelete);
        countedMarkers.removeAll(instance.forcedOutRange);
        countedMarkers.removeAll(instance.forcedDuplicate);
        outOfRangeMarkers.addAll(instance.forcedOutRange);
        doubleMarkers.addAll(instance.forcedDuplicate);

        for (CellCntrMarker cMarker : countedMarkers) {
            if (!larger.contains(cMarker.getX(), cMarker.getY()) && //
                    !instance.forcedInRange.contains(cMarker)) {
                if (justOutOfRange.contains(cMarker.getX(), cMarker.getY())) {
                    outOfRangeMarkers.add(cMarker);
                    instance.forcedOutRange.remove(cMarker);
                }
                instance.invisiblePoints.add(cMarker);
            } else {
                instance.forcedInRange.remove(cMarker);
            }
        }
        countedMarkers.removeAll(instance.invisiblePoints);
        List<CellCntrMarker> moveToType3 = new ArrayList<>();
        Set<Roi> rois = new HashSet<>();
        for (CellCntrMarker marker : countedMarkers) {
            for (CellCntrMarker otherMarker : countedMarkers) {
                if (marker != otherMarker && //
                        !moveToType3.contains(marker) && !moveToType3.contains(otherMarker)) {
                    if (areTwoMarkersSame(marker, otherMarker)) {
                        moveToType3.add(otherMarker);
                        rois.add(marker.getRoi());
                        rois.add(otherMarker.getRoi());
                    }
                }
            }
        }
        moveToType3.removeAll(instance.forcedInRange);
        countedMarkers.removeAll(moveToType3);
        countedMarkers.addAll(instance.forcedInRange);
        doubleMarkers.addAll(moveToType3);

        updateGui();
        instance.autoExportMarkers();
    }

    public static void close() {
        if (instance != null) {
            try {
                instance.setVisible(false);
                instance.counterImg.close();
                instance.dispose();
            } catch (Exception e) {
                // just to besure
            }
            instance = null;
        }
    }

    private static double distanceBetweenPointAndMarker(CellCntrMarker l1, double x, double y) {
        if (l1 == null) {
            return Double.MAX_VALUE;
        }
        double offset = Math.abs(l1.getZ() - instance.counterImg.getCurrentSlice()) * 20;
        return Math.sqrt(//
                (x - l1.getX()) * (x - l1.getX()) + //
                        (y - l1.getY()) * (y - l1.getY()))
                + offset;
    }

    private static double distanceBetweenTwoMarkers(CellCntrMarker current, CellCntrMarker other) {
        return Math.sqrt(//
                (current.getX() - other.getX()) * (current.getX() - other.getX()) + //
                        (current.getY() - other.getY()) * (current.getY() - other.getY()));
    }

    private static int getContainedPointsCount(Roi roi) {
        Rectangle bounds = roi.getBounds();
        int count = 0;
        int xOffset = bounds.x;
        int yOffset = bounds.y;
        for (int y = 0; y < bounds.height; y++) {
            for (int x = 0; x < bounds.width; x++) {
                if (roi.contains(x + xOffset, y + yOffset)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int getContainedPointsCount(Roi roi1, Roi roi2) {
        Rectangle bounds = roi1.getBounds();
        int count = 0;
        int xOffset = bounds.x;
        int yOffset = bounds.y;
        for (int y = 0; y < bounds.height; y++) {
            for (int x = 0; x < bounds.width; x++) {
                if (roi1.contains(x + xOffset, y + yOffset) && roi2.contains(x + xOffset, y + yOffset)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static void initializeCellImage() {
        instance.initializeImage();
        instance.showAllCheck.setSelected(true);
        updateGui();
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public static void setState(AnalysisState state) {
        instance.setAnalysisState(state);
    }

    public static void setType(String type) {
        if (instance == null || instance.ic == null || type == null) {
            return;
        }
        int index;
        if (COUNTED.equals(type)) {
            index = 0;
        } else if (OUT_OF_RANGE.equals(type)) {
            index = 1;
        } else if (DOUBLE.equals(type)) {
            index = 2;
        } else if (TAIL_RETRACT.equals(type)) {
            index = 6;
        } else {
            index = Integer.parseInt(type) - 1;
        }
        int buttons = instance.dynRadioVector.size();
        if (index < 0 || index >= buttons) {
            return;
        }
        JRadioButton rbutton = instance.dynRadioVector.elementAt(index);
        instance.radioGrp.setSelected(rbutton.getModel(), true);
        instance.currentMarkerVector = (CellCntrMarkerVector) instance.typeVector.get(index);
        instance.ic.setCurrentMarkerVector(instance.currentMarkerVector);
    }

    private static void updateGui() {
        instance.populateTxtFields();
        instance.invalidate();
        instance.validate();
        instance.repaint();
        instance.ic.repaint();
    }

    private Vector typeVector;

    private Vector<JRadioButton> dynRadioVector;

    private final Vector<JTextField> txtFieldVector;

    private CellCntrMarkerVector markerVector;

    private CellCntrMarkerVector currentMarkerVector;

    private JPanel dynPanel;

    private JPanel dynButtonPanel;

    private JPanel statButtonPanel;

    private JPanel dynTxtPanel;

    private JCheckBox delCheck;

    private JCheckBox newCheck;

    private JCheckBox numbersCheck;

    private JCheckBox showAllCheck;

    private ButtonGroup radioGrp;

    private JSeparator separator;

    private JButton addButton;

    private JButton removeButton;

    private JButton initializeButton;

    private JButton resultsButton;

    private JButton deleteButton;

    private JButton resetButton;

    private JButton exportButton;

    private JButton loadButton;

    private JButton exportimgButton;

    private JButton measureButton;

    private JButton germButton;

    private boolean keepOriginal = false;

    private CellCntrImageCanvas ic;

    private ImagePlus img;

    private ImagePlus counterImg;

    private GridLayout dynGrid;

    private final boolean isJava14;

    boolean newGermBandActive = false;

    long lastSave;

    private List<CellCntrMarker> invisiblePoints = new ArrayList<>();

    private Roi larger;

    private Roi justOutOfRange;

    private String tailRetraction = "";

    private AnalysisState analysisState = AnalysisState.NORMAL;

    private final List<CellCntrMarker> forcedInRange = new ArrayList<>();

    private final List<CellCntrMarker> forcedOutRange = new ArrayList<>();

    private final List<CellCntrMarker> forcedDuplicate = new ArrayList<>();

    private final List<CellCntrMarker> forcedDelete = new ArrayList<>();

    public CellCounter() {
        super("Cell Counter Julia");
        this.isJava14 = IJ.isJava14();
        if (!this.isJava14) {
            IJ.showMessage("You are using a pre 1.4 version of java, exporting and loading marker data is disabled");
        }
        setResizable(false);
        this.typeVector = new Vector();
        this.txtFieldVector = new Vector();
        this.dynRadioVector = new Vector();
        initGUI();
        populateTxtFields();
        instance = this;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        String command = event.getActionCommand();

        if (command.compareTo(ADD) == 0) {
            int i = this.dynRadioVector.size() + 1;
            this.dynGrid.setRows(i);
            this.dynButtonPanel.add(makeDynRadioButton(i));
            validateLayout();

            if (this.ic != null) {
                this.ic.setTypeVector(this.typeVector);
            }
        } else if (command.compareTo(REMOVE) == 0) {
            if (this.dynRadioVector.size() > 1) {
                JRadioButton rbutton = this.dynRadioVector.lastElement();
                this.dynButtonPanel.remove(rbutton);
                this.radioGrp.remove(rbutton);
                this.dynRadioVector.removeElementAt(this.dynRadioVector.size() - 1);
                this.dynGrid.setRows(this.dynRadioVector.size());
            }
            if (this.txtFieldVector.size() > 1) {
                JTextField field = this.txtFieldVector.lastElement();
                this.dynTxtPanel.remove(field);
                this.txtFieldVector.removeElementAt(this.txtFieldVector.size() - 1);
            }
            if (this.typeVector.size() > 1) {
                this.typeVector.removeElementAt(this.typeVector.size() - 1);
            }
            validateLayout();

            if (this.ic != null) {
                this.ic.setTypeVector(this.typeVector);
            }
        } else if (command.compareTo(INITIALIZE) == 0) {
            initializeImage();
        } else if (command.equals(TAIL_RETRACT)) { // COUNT
            if (this.ic == null) {
                IJ.error("You need to initialize first");
                return;
            }
            this.currentMarkerVector = (CellCntrMarkerVector) this.typeVector.get(6);
            this.ic.setCurrentMarkerVector(this.currentMarkerVector);
        } else if (command.equals(COUNTED)) { // COUNT
            if (this.ic == null) {
                IJ.error("You need to initialize first");
                return;
            }
            this.currentMarkerVector = (CellCntrMarkerVector) this.typeVector.get(0);
            this.ic.setCurrentMarkerVector(this.currentMarkerVector);
        } else if (command.equals(OUT_OF_RANGE)) { // COUNT
            if (this.ic == null) {
                IJ.error("You need to initialize first");
                return;
            }
            this.currentMarkerVector = (CellCntrMarkerVector) this.typeVector.get(1);
            this.ic.setCurrentMarkerVector(this.currentMarkerVector);
        } else if (command.equals(DOUBLE)) { // COUNT
            if (this.ic == null) {
                IJ.error("You need to initialize first");
                return;
            }
            this.currentMarkerVector = (CellCntrMarkerVector) this.typeVector.get(2);
            this.ic.setCurrentMarkerVector(this.currentMarkerVector);
        } else if (command.startsWith("Type")) { // COUNT
            if (this.ic == null) {
                IJ.error("You need to initialize first");
                return;
            }
            int index = Integer.parseInt(command.substring(command.indexOf(" ") + 1, command.length()));
            // ic.setDelmode(false); // just in case
            this.currentMarkerVector = (CellCntrMarkerVector) this.typeVector.get(index - 1);
            this.ic.setCurrentMarkerVector(this.currentMarkerVector);
        } else if (command.compareTo(DELETE) == 0) {
            this.ic.removeLastMarker();
        } else if (command.compareTo(RESET) == 0) {
            reset();
        } else if (command.compareTo(RESULTS) == 0) {
            report();
        } else if (command.compareTo(EXPORTMARKERS) == 0) {
            exportMarkers();
        } else if (command.compareTo(LOADMARKERS) == 0) {
            if (this.ic == null) {
                initializeImage();
            }
            loadMarkers();
            validateLayout();
        } else if (command.compareTo(EXPORTIMG) == 0) {
            this.ic.imageWithMarkers().show();
        } else if (command.compareTo(MEASURE) == 0) {
            measure();
        } else if (command.compareTo(NEW_GERM_BAND) == 0) {
            newGermBand();
        }
        if (this.ic != null) {
            this.ic.repaint();
        }
        populateTxtFields();
    }

    private void addToTypeVector(int index, CellCntrMarker marker) {
        if (index < 0) {
            ((CellCntrMarkerVector) this.typeVector.get(0)).remove(marker);
            ((CellCntrMarkerVector) this.typeVector.get(1)).remove(marker);
            ((CellCntrMarkerVector) this.typeVector.get(2)).remove(marker);
        } else {
            if (marker == null) {
                "".toString();
            }
            ((CellCntrMarkerVector) this.typeVector.get(index)).addMarker(marker);
            ((CellCntrMarkerVector) this.typeVector.get((index + 1) % 3)).remove(marker);
            ((CellCntrMarkerVector) this.typeVector.get((index + 2) % 3)).remove(marker);
        }
    }

    public void autoExportMarkers() {
        long newTime = System.currentTimeMillis();
        if (newTime - this.lastSave < 5000) {
            return; // save only every 5 sec
        }
        this.lastSave = newTime;
        String filename = fileInfo().fileName;
        String nameWithoutPrefix = filename.substring(0, filename.lastIndexOf("."));
        File file = new File(new File(fileInfo().directory, nameWithoutPrefix), nameWithoutPrefix + "_000_cellcounter.xml");
        String filePath = file.getAbsolutePath();
        int counter = 0;
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdir();
        }
        while (new File(filePath).exists() && counter < 900) {
            counter++;
            file = new File(new File(fileInfo().directory, nameWithoutPrefix), nameWithoutPrefix + "_" + String.format("%03d", counter) + "_cellcounter.xml");
            filePath = file.getAbsolutePath();
        }
        WriteXML wxml = new WriteXML(filePath);
        wxml.writeXML(this.img.getTitle(), this.typeVector, this.typeVector.indexOf(this.currentMarkerVector));
    }

    public void clickWithCtrl(int x, int y) {
        CellCntrMarker nearestCounted = findNearest(x, y, (CellCntrMarkerVector) this.typeVector.get(0));
        CellCntrMarker nearestDuplicate = findNearest(x, y, (CellCntrMarkerVector) this.typeVector.get(2));
        CellCntrMarker nearestOutOfRange = findNearest(x, y, (CellCntrMarkerVector) this.typeVector.get(1));
        double distCount = distanceBetweenPointAndMarker(nearestCounted, x, y);
        double distDupl = distanceBetweenPointAndMarker(nearestDuplicate, x, y);
        double distOutOfRange = distanceBetweenPointAndMarker(nearestOutOfRange, x, y);
        if (Math.min(Math.min(distCount, distDupl), distOutOfRange) > 10d) {
            IJ.showStatus("no near cell " + distCount + " and " + distDupl);
            return;
        }
        if (distOutOfRange < distDupl && distOutOfRange < distCount) {
            removeFromForced(nearestOutOfRange);
            this.forcedInRange.add(nearestOutOfRange);
            addToTypeVector(0, nearestOutOfRange);
        } else if (distCount < distDupl && distCount < distOutOfRange) {
            removeFromForced(nearestCounted);
            this.forcedDuplicate.add(nearestCounted);
            addToTypeVector(2, nearestCounted);
        } else if (nearestDuplicate != null) {
            removeFromForced(nearestDuplicate);
            this.forcedDelete.add(nearestDuplicate);
            addToTypeVector(-1, nearestDuplicate);
        }
        instance.autoExportMarkers();
    }

    public void clickWithShift(int x, int y) {
        if (this.larger == null) {
            return;
        }
        boolean expandMode = this.larger.contains(x, y);
        int expansionSize = 0;
        ShapeRoi expansion = null;
        int circleSize = 0;
        ShapeRoi largerRoi = largerRoi();
        while (expandMode && expansionSize < 50 && circleSize < 100) {
            circleSize += 10;
            ShapeRoi localRoi = largerRoi;
            expansion = new ShapeRoi(new OvalRoi(x - circleSize / 2, y - circleSize / 2, circleSize, circleSize))
                    .xor(new ShapeRoi(new OvalRoi(x - circleSize / 2, y - circleSize / 2, circleSize, circleSize)).and(localRoi));
            try {
                expansionSize = expansion.getContainedPoints().length;
            } catch (Exception e) {
                expansionSize = 0;
            }
        }
        if (expandMode && expansion != null) {
            largerRoi.or(expansion);
        }
        circleSize = 0;
        ShapeRoi substract = null;
        while (!expandMode && expansionSize < 50 && circleSize < 100) {
            circleSize += 10;
            ShapeRoi localRoi = largerRoi;
            substract = new ShapeRoi(new OvalRoi(x - circleSize / 2, y - circleSize / 2, circleSize, circleSize)).and(localRoi);
            try {
                expansionSize = substract.getContainedPoints().length;
            } catch (Exception e) {
                expansionSize = 0;
            }
        }
        if (!expandMode && substract != null) {
            largerRoi.xor(substract);
        }
        this.justOutOfRange = RoiEnlarger.enlarge(new ShapeRoi(largerRoi), 15d);
        checkInRegion(largerRoi, this.justOutOfRange);
        RoiManager.getInstance().reset();
        RoiManager.getInstance().addRoi(largerRoi);
    }

    public void clickWithShiftCtrl(int x, int y) {
        CellCntrMarker nearestCounted = findNearest(x, y, (CellCntrMarkerVector) this.typeVector.get(0));
        CellCntrMarker nearestDuplicate = findNearest(x, y, (CellCntrMarkerVector) this.typeVector.get(2));
        CellCntrMarker nearestOutOfRange = findNearest(x, y, (CellCntrMarkerVector) this.typeVector.get(1));
        double distCount = distanceBetweenPointAndMarker(nearestCounted, x, y);
        double distDupl = distanceBetweenPointAndMarker(nearestDuplicate, x, y);
        double distOutOfRange = distanceBetweenPointAndMarker(nearestOutOfRange, x, y);
        if (Math.min(Math.min(distCount, distDupl), distOutOfRange) > 10d) {
            IJ.showStatus("no near cell " + distCount + " and " + distDupl);
            return;
        }
        if (distOutOfRange < distDupl && distOutOfRange < distCount) {
            removeFromForced(nearestOutOfRange);
            this.forcedDelete.add(nearestOutOfRange);
            addToTypeVector(-1, nearestOutOfRange);
        } else if (distCount < distDupl && distCount < distOutOfRange) {
            removeFromForced(nearestCounted);
            this.forcedOutRange.add(nearestCounted);
            addToTypeVector(1, nearestCounted);
        } else if (nearestDuplicate != null) {
            removeFromForced(nearestDuplicate);
            this.forcedInRange.add(nearestDuplicate);
            addToTypeVector(0, nearestDuplicate);
        }
        instance.autoExportMarkers();
    }

    public void defineNewGermBand(int x, int y) {
        if (RoiManager.getInstance() == null) {
            RoiManager.getRoiManager();
        }
        this.larger = new ShapeRoi(new OvalRoi(x - 40, y - 40, 80, 80));
        this.justOutOfRange = RoiEnlarger.enlarge(new ShapeRoi(this.larger), 15d);
        RoiManager.getInstance().reset();
        RoiManager.getInstance().addRoi(this.larger);
        RoiManager.getInstance().runCommand("show all");

        checkInRegion(this.larger, this.justOutOfRange);
        this.newGermBandActive = false;
    }

    public void exportMarkers() {
        String filePath = getFilePath(new JFrame(), "Save Marker File (.xml)", SAVE);
        if (!filePath.endsWith(".xml")) {
            filePath += ".xml";
        }
        WriteXML wxml = new WriteXML(filePath);
        wxml.writeXML(this.img.getTitle(), this.typeVector, this.typeVector.indexOf(this.currentMarkerVector));
    }

    private FileInfo fileInfo() {
        FileInfo originalFileInfo = this.img.getOriginalFileInfo();
        if (originalFileInfo == null) {
            return this.img.getFileInfo();
        }
        return originalFileInfo;
    }

    private CellCntrMarker findNearest(int x, int y, CellCntrMarkerVector current) {
        CellCntrMarker nearestCounted = null;
        double distance = Double.MAX_VALUE;
        for (CellCntrMarker cellCntrMarker : current) {
            double dist = distanceBetweenPointAndMarker(cellCntrMarker, x, y);
            if (dist < distance) {
                distance = dist;
                nearestCounted = cellCntrMarker;
            }
        }
        return nearestCounted;
    }

    private void forceBlockMoveToOne() {
        ImageWindow window = WindowManager.getCurrentImage().getWindow();
        for (Component component : window.getComponents()) {
            if (component instanceof ScrollbarWithLabel) {
                ((ScrollbarWithLabel) component).setBlockIncrement(1);
            }
        }
    }

    public AnalysisState getAnalysisState() {
        return this.analysisState;
    }

    public Vector getButtonVector() {
        return this.dynRadioVector;
    }

    public CellCntrMarkerVector getCurrentMarkerVector() {
        return this.currentMarkerVector;
    }

    private String getFilePath(JFrame parent, String dialogMessage, int dialogType) {
        switch (dialogType) {
            case SAVE:
                dialogMessage = "Save " + dialogMessage;
                break;
            case OPEN:
                dialogMessage = "Open " + dialogMessage;
                break;
        }
        FileDialog fd;
        String[] filePathComponents = new String[2];
        int PATH = 0;
        int FILE = 1;
        fd = new FileDialog(parent, dialogMessage, dialogType);
        switch (dialogType) {
            case OPEN:
                fd.setDirectory(fileInfo().directory);
                break;
            case SAVE:
                String filenameSave = fileInfo().fileName;
                fd.setDirectory(fileInfo().directory);
                fd.setFile(filenameSave.substring(0, filenameSave.lastIndexOf(".")) + "_cellcounter.xml");
                break;
        }
        fd.setVisible(true);
        filePathComponents[PATH] = fd.getDirectory();
        filePathComponents[FILE] = fd.getFile();
        return filePathComponents[PATH] + filePathComponents[FILE];
    }

    private void initGUI() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        GridBagLayout gb = new GridBagLayout();
        getContentPane().setLayout(gb);

        this.radioGrp = new ButtonGroup();// to group the radiobuttons

        this.dynGrid = new GridLayout(8, 1);
        this.dynGrid.setVgap(2);

        // this panel will keep the dynamic GUI parts
        this.dynPanel = new JPanel();
        this.dynPanel.setBorder(BorderFactory.createTitledBorder("Counters"));
        this.dynPanel.setLayout(gb);

        // this panel keeps the radiobuttons
        this.dynButtonPanel = new JPanel();
        this.dynButtonPanel.setLayout(this.dynGrid);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.ipadx = 5;
        gb.setConstraints(this.dynButtonPanel, gbc);
        this.dynPanel.add(this.dynButtonPanel);

        // this panel keeps the score
        this.dynTxtPanel = new JPanel();
        this.dynTxtPanel.setLayout(this.dynGrid);
        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.ipadx = 5;
        gb.setConstraints(this.dynTxtPanel, gbc);
        this.dynPanel.add(this.dynTxtPanel);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.ipadx = 5;
        gb.setConstraints(this.dynPanel, gbc);
        getContentPane().add(this.dynPanel);

        this.dynButtonPanel.add(makeDynRadioButton(1));
        this.dynButtonPanel.add(makeDynRadioButton(2));
        this.dynButtonPanel.add(makeDynRadioButton(3));
        this.dynButtonPanel.add(makeDynRadioButton(4));
        this.dynButtonPanel.add(makeDynRadioButton(5));
        this.dynButtonPanel.add(makeDynRadioButton(6));
        this.dynButtonPanel.add(makeDynRadioButton(7));
        this.dynButtonPanel.add(makeDynRadioButton(8));

        // create a "static" panel to hold control buttons
        this.statButtonPanel = new JPanel();
        this.statButtonPanel.setBorder(BorderFactory.createTitledBorder("Actions"));
        this.statButtonPanel.setLayout(gb);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.newCheck = new JCheckBox(KEEPORIGINAL);
        this.newCheck.setToolTipText("Keep original");
        this.newCheck.setSelected(false);
        this.newCheck.addItemListener(this);
        gb.setConstraints(this.newCheck, gbc);
        this.statButtonPanel.add(this.newCheck);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.initializeButton = makeButton(INITIALIZE, "Initialize image to count");
        gb.setConstraints(this.initializeButton, gbc);
        this.statButtonPanel.add(this.initializeButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(3, 0, 3, 0);
        this.separator = new JSeparator(SwingConstants.HORIZONTAL);
        this.separator.setPreferredSize(new Dimension(1, 1));
        gb.setConstraints(this.separator, gbc);
        this.statButtonPanel.add(this.separator);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.addButton = makeButton(ADD, "add a counter type");
        gb.setConstraints(this.addButton, gbc);
        this.statButtonPanel.add(this.addButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.removeButton = makeButton(REMOVE, "remove last counter type");
        gb.setConstraints(this.removeButton, gbc);
        this.statButtonPanel.add(this.removeButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.insets = new Insets(3, 0, 3, 0);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.separator = new JSeparator(SwingConstants.HORIZONTAL);
        this.separator.setPreferredSize(new Dimension(1, 1));
        gb.setConstraints(this.separator, gbc);
        this.statButtonPanel.add(this.separator);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.deleteButton = makeButton(DELETE, "delete last marker");
        this.deleteButton.setEnabled(false);
        gb.setConstraints(this.deleteButton, gbc);
        this.statButtonPanel.add(this.deleteButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.delCheck = new JCheckBox(DELMODE);
        this.delCheck.setToolTipText("When selected\nclick on the marker\nyou want to remove");
        this.delCheck.setSelected(false);
        this.delCheck.addItemListener(this);
        this.delCheck.setEnabled(false);
        gb.setConstraints(this.delCheck, gbc);
        this.statButtonPanel.add(this.delCheck);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(3, 0, 3, 0);
        this.separator = new JSeparator(SwingConstants.HORIZONTAL);
        this.separator.setPreferredSize(new Dimension(1, 1));
        gb.setConstraints(this.separator, gbc);
        this.statButtonPanel.add(this.separator);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.resultsButton = makeButton(RESULTS, "show results in results table");
        this.resultsButton.setEnabled(false);
        gb.setConstraints(this.resultsButton, gbc);
        this.statButtonPanel.add(this.resultsButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.resetButton = makeButton(RESET, "reset all counters");
        this.resetButton.setEnabled(false);
        gb.setConstraints(this.resetButton, gbc);
        this.statButtonPanel.add(this.resetButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(3, 0, 3, 0);
        this.separator = new JSeparator(SwingConstants.HORIZONTAL);
        this.separator.setPreferredSize(new Dimension(1, 1));
        gb.setConstraints(this.separator, gbc);
        this.statButtonPanel.add(this.separator);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.numbersCheck = new JCheckBox(SHOWNUMBERS);
        this.numbersCheck.setToolTipText("When selected, numbers are shown");
        this.numbersCheck.setSelected(true);
        this.numbersCheck.setEnabled(false);
        this.numbersCheck.addItemListener(this);
        gb.setConstraints(this.numbersCheck, gbc);
        this.statButtonPanel.add(this.numbersCheck);

        this.showAllCheck = new JCheckBox(SHOWALL);
        this.showAllCheck.setToolTipText("When selected, all stack markers are shown");
        this.showAllCheck.setSelected(false);
        this.showAllCheck.setEnabled(false);
        this.showAllCheck.addItemListener(this);
        gb.setConstraints(this.showAllCheck, gbc);
        this.statButtonPanel.add(this.showAllCheck);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.exportButton = makeButton(EXPORTMARKERS, "Save markers to file");
        this.exportButton.setEnabled(false);
        gb.setConstraints(this.exportButton, gbc);
        this.statButtonPanel.add(this.exportButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.loadButton = makeButton(LOADMARKERS, "Load markers from file");
        if (!this.isJava14) {
            this.loadButton.setEnabled(false);
        }
        gb.setConstraints(this.loadButton, gbc);
        this.statButtonPanel.add(this.loadButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.exportimgButton = makeButton(EXPORTIMG, "Export image with markers");
        this.exportimgButton.setEnabled(false);
        gb.setConstraints(this.exportimgButton, gbc);
        this.statButtonPanel.add(this.exportimgButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(3, 0, 3, 0);
        this.separator = new JSeparator(SwingConstants.HORIZONTAL);
        this.separator.setPreferredSize(new Dimension(1, 1));
        gb.setConstraints(this.separator, gbc);
        this.statButtonPanel.add(this.separator);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.measureButton = makeButton(MEASURE, "Measure pixel intensity of marker points");
        this.measureButton.setEnabled(false);
        gb.setConstraints(this.measureButton, gbc);
        this.statButtonPanel.add(this.measureButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        this.germButton = makeButton(NEW_GERM_BAND, "Start draw manual germ band");
        this.germButton.setEnabled(false);
        gb.setConstraints(this.germButton, gbc);
        this.statButtonPanel.add(this.germButton);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.ipadx = 5;
        gb.setConstraints(this.statButtonPanel, gbc);
        getContentPane().add(this.statButtonPanel);

        Runnable runner = new GUIShower(this);
        EventQueue.invokeLater(runner);
    }

    private void initializeImage() {
        reset();
        this.img = WindowManager.getCurrentImage();
        boolean v139t = IJ.getVersion().compareTo("1.39t") >= 0;
        if (this.img == null) {
            IJ.noImage();
        } else if (this.img.getStackSize() == 1) {
            ImageProcessor ip = this.img.getProcessor();
            ip.resetRoi();
            if (this.keepOriginal) {
                ip = ip.crop();
            }
            this.counterImg = new ImagePlus("Counter Window - " + this.img.getTitle(), ip);
            Vector displayList = v139t ? this.img.getCanvas().getDisplayList() : null;
            this.ic = new CellCntrImageCanvas(this.counterImg, this.typeVector, this, displayList);
            new ImageWindow(this.counterImg, this.ic);
        } else if (this.img.getStackSize() > 1) {
            ImageStack stack = this.img.getStack();
            int size = stack.getSize();
            ImageStack counterStack = this.img.createEmptyStack();
            for (int i = 1; i <= size; i++) {
                ImageProcessor ip = stack.getProcessor(i);
                if (this.keepOriginal) {
                    ip = ip.crop();
                }
                counterStack.addSlice(stack.getSliceLabel(i), ip);
            }
            this.counterImg = new ImagePlus("Counter Window - " + this.img.getTitle(), counterStack);
            this.counterImg.setDimensions(this.img.getNChannels(), this.img.getNSlices(), this.img.getNFrames());
            if (this.img.isComposite()) {
                this.counterImg = new CompositeImage(this.counterImg, ((CompositeImage) this.img).getMode());
                ((CompositeImage) this.counterImg).copyLuts(this.img);
            }
            this.counterImg.setOpenAsHyperStack(this.img.isHyperStack());
            Vector displayList = v139t ? this.img.getCanvas().getDisplayList() : null;
            this.ic = new CellCntrImageCanvas(this.counterImg, this.typeVector, this, displayList);
            new StackWindow(this.counterImg, this.ic);
        }
        if (!this.keepOriginal) {
            this.img.changes = false;
            this.img.close();
        }
        this.delCheck.setEnabled(true);
        this.numbersCheck.setEnabled(true);
        this.showAllCheck.setSelected(false);
        if (this.counterImg.getStackSize() > 1) {
            this.showAllCheck.setEnabled(true);
        }
        this.addButton.setEnabled(true);
        this.removeButton.setEnabled(true);
        this.resultsButton.setEnabled(true);
        this.deleteButton.setEnabled(true);
        this.resetButton.setEnabled(true);
        if (this.isJava14) {
            this.exportButton.setEnabled(true);
        }
        this.exportimgButton.setEnabled(true);
        this.measureButton.setEnabled(true);
        this.germButton.setEnabled(true);
        CellCounter.setType("1");
        forceBlockMoveToOne();
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (e.getItem().equals(this.delCheck)) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                this.ic.setDelmode(true);
            } else {
                this.ic.setDelmode(false);
            }
        } else if (e.getItem().equals(this.newCheck)) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                this.keepOriginal = true;
            } else {
                this.keepOriginal = false;
            }
        } else if (e.getItem().equals(this.numbersCheck)) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                this.ic.setShowNumbers(true);
            } else {
                this.ic.setShowNumbers(false);
            }
            this.ic.repaint();
        } else if (e.getItem().equals(this.showAllCheck)) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                this.ic.setShowAll(true);
            } else {
                this.ic.setShowAll(false);
            }
            this.ic.repaint();
        }
    }

    private ShapeRoi largerRoi() {
        if (this.larger instanceof ShapeRoi) {
            return (ShapeRoi) this.larger;
        }
        return new ShapeRoi(this.larger);
    }

    public void loadMarkers() {
        String filePath = getFilePath(new JFrame(), "Select Marker File", OPEN);
        ReadXML rxml = new ReadXML(filePath);
        String storedfilename = rxml.readImgProperties(ReadXML.IMAGE_FILE_PATH);
        if (storedfilename.equals(this.img.getTitle())) {
            Vector loadedvector = rxml.readMarkerData();
            this.typeVector = loadedvector;
            this.ic.setTypeVector(this.typeVector);
            int index = Integer.parseInt(rxml.readImgProperties(ReadXML.CURRENT_TYPE));
            this.currentMarkerVector = (CellCntrMarkerVector) this.typeVector.get(index);
            this.ic.setCurrentMarkerVector(this.currentMarkerVector);

            while (this.dynRadioVector.size() > this.typeVector.size()) {
                if (this.dynRadioVector.size() > 1) {
                    JRadioButton rbutton = this.dynRadioVector.lastElement();
                    this.dynButtonPanel.remove(rbutton);
                    this.radioGrp.remove(rbutton);
                    this.dynRadioVector.removeElementAt(this.dynRadioVector.size() - 1);
                    this.dynGrid.setRows(this.dynRadioVector.size());
                }
                if (this.txtFieldVector.size() > 1) {
                    JTextField field = this.txtFieldVector.lastElement();
                    this.dynTxtPanel.remove(field);
                    this.txtFieldVector.removeElementAt(this.txtFieldVector.size() - 1);
                }
            }
            JRadioButton butt = this.dynRadioVector.get(index);
            butt.setSelected(true);

        } else {
            IJ.error("These Markers do not belong to the current image");
        }
    }

    private JButton makeButton(String name, String tooltip) {
        JButton jButton = new JButton(name);
        jButton.setToolTipText(tooltip);
        jButton.addActionListener(this);
        return jButton;
    }

    private JTextField makeDynamicTextArea() {
        JTextField txtFld = new JTextField();
        txtFld.setHorizontalAlignment(SwingConstants.CENTER);
        txtFld.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        txtFld.setEditable(false);
        txtFld.setText("0");
        this.txtFieldVector.add(txtFld);
        return txtFld;
    }

    private JRadioButton makeDynRadioButton(int id) {
        String text = "Type " + id;
        if (id == 1) {
            text = COUNTED;
        } else if (id == 2) {
            text = OUT_OF_RANGE;
        } else if (id == 3) {
            text = DOUBLE;
        } else if (id == 7) {
            text = TAIL_RETRACT;
        }
        JRadioButton jrButton = new JRadioButton(text);
        jrButton.addActionListener(this);
        this.dynRadioVector.add(jrButton);
        this.radioGrp.add(jrButton);
        this.markerVector = new CellCntrMarkerVector(id);
        this.typeVector.add(this.markerVector);
        this.dynTxtPanel.add(makeDynamicTextArea());
        jrButton.setBackground(this.markerVector.getColor());
        return jrButton;
    }

    public void measure() {
        this.ic.measure();
    }

    private void newGermBand() {
        this.newGermBandActive = true;
    }

    void populateTxtFields() {
        ListIterator it = this.typeVector.listIterator();
        while (it.hasNext()) {
            int index = it.nextIndex();
            CellCntrMarkerVector markerVector = (CellCntrMarkerVector) it.next();
            int count = markerVector.size();
            JTextField tArea = this.txtFieldVector.get(index);
            if (index == 6) {
                tArea.setText(this.tailRetraction);
            } else {
                tArea.setText("" + count);
            }
        }
        validateLayout();
    }

    private void removeFromForced(CellCntrMarker marker) {
        this.forcedDuplicate.remove(marker);
        this.forcedDelete.remove(marker);
        this.forcedOutRange.remove(marker);
        this.forcedInRange.remove(marker);
    }

    public void report() {
        String labels = "Slice\t";
        boolean isStack = this.counterImg.getStackSize() > 1;
        // add the types according to the button vector!!!!
        ListIterator it = this.dynRadioVector.listIterator();
        while (it.hasNext()) {
            JRadioButton button = (JRadioButton) it.next();
            String str = button.getText(); // System.out.println(str);
            labels = labels.concat(str + "\t");
        }
        IJ.setColumnHeadings(labels);
        String results = "";
        if (isStack) {
            for (int slice = 1; slice <= this.counterImg.getStackSize(); slice++) {
                results = "";
                ListIterator mit = this.typeVector.listIterator();
                int types = this.typeVector.size();
                int[] typeTotals = new int[types];
                while (mit.hasNext()) {
                    int type = mit.nextIndex();
                    CellCntrMarkerVector mv = (CellCntrMarkerVector) mit.next();
                    ListIterator tit = mv.listIterator();
                    while (tit.hasNext()) {
                        CellCntrMarker m = (CellCntrMarker) tit.next();
                        if (m.getZ() == slice) {
                            typeTotals[type]++;
                        }
                    }
                }
                results = results.concat(slice + "\t");
                for (int typeTotal : typeTotals) {
                    results = results.concat(typeTotal + "\t");
                }
                IJ.write(results);
            }
            IJ.write("");
        }
        results = "Total\t";
        ListIterator mit = this.typeVector.listIterator();
        while (mit.hasNext()) {
            CellCntrMarkerVector mv = (CellCntrMarkerVector) mit.next();
            int count = mv.size();
            results = results.concat(count + "\t");
        }
        IJ.write(results);
    }

    public void reset() {
        if (this.typeVector.size() < 1) {
            return;
        }
        this.forcedDelete.clear();
        this.forcedDuplicate.clear();
        this.invisiblePoints = new ArrayList<>();
        ListIterator mit = this.typeVector.listIterator();
        while (mit.hasNext()) {
            CellCntrMarkerVector mv = (CellCntrMarkerVector) mit.next();
            mv.clear();
        }
        if (this.ic != null) {
            this.ic.repaint();
        }
    }

    public void setAnalysisState(AnalysisState state) {
        this.analysisState = state;
    }

    public void setButtonVector(Vector buttonVector) {
        this.dynRadioVector = buttonVector;
    }

    public void setCurrentMarkerVector(CellCntrMarkerVector currentMarkerVector) {
        this.currentMarkerVector = currentMarkerVector;
    }

    public void tailRetractionMarker(CellCntrMarkerVector currentMarkerVector) {
        while (currentMarkerVector.size() > 3) {
            currentMarkerVector.remove(0);
        }
        if (currentMarkerVector.size() < 3) {
            return;
        }
        CellCntrMarker p1 = currentMarkerVector.get(0);
        CellCntrMarker p2 = currentMarkerVector.get(1);
        CellCntrMarker p3 = currentMarkerVector.get(2);
        double dist1 = distanceBetweenTwoMarkers(p1, p2);
        double dist2 = distanceBetweenTwoMarkers(p1, p3);
        double dist3 = distanceBetweenTwoMarkers(p2, p3);
        CellCntrMarker l1;
        CellCntrMarker l2;
        CellCntrMarker t;
        double longest;
        if (dist1 > dist2 && dist1 > dist3) {
            l1 = p1;
            l2 = p2;
            t = p3;
            longest = dist1;
        } else if (dist2 > dist1 && dist2 > dist3) {
            l1 = p1;
            l2 = p3;
            t = p2;
            longest = dist2;
        } else /* if (dist3 > dist1 && dist3 > dist2) */ {
            l1 = p2;
            l2 = p3;
            t = p1;
            longest = dist3;
        }
        double deltaX = l1.getX() - l2.getX();
        double deltaY = l1.getY() - l2.getY();
        double a = deltaY / deltaX;
        double b = l1.getY() - a * l1.getX();
        double aRechtDrauf = -(deltaX / deltaY);
        double bRechtDrauf = t.getY() - aRechtDrauf * t.getX();
        // y = ax + b
        // a = (y - b) / x
        // x = (y - b) / a
        // a1.x + b1 = a2.x + b2 => x=(b2-b1)/(a1-a2)
        double x = (bRechtDrauf - b) / (a - aRechtDrauf);
        double y = aRechtDrauf * x + bRechtDrauf;
        CellCntrMarker basePoint = new CellCntrMarker((int) x, (int) y, p1.getZ(), p1.getRealZ(), null);
        currentMarkerVector.add(0, basePoint);

        double sdist1 = distanceBetweenPointAndMarker(l1, x, y);
        double sdist2 = distanceBetweenPointAndMarker(l2, x, y);
        double tailDistance = Math.max(sdist1, sdist2);
        double tailRetraction = Math.abs(tailDistance / longest - 1);
        DecimalFormat decimalFormat = new DecimalFormat("###0.0");
        NumberFormat percentInstance = NumberFormat.getPercentInstance();
        IJ.showStatus("tail retraction = " + //
                percentInstance.format(tailRetraction) + //
                " =(" + decimalFormat.format(tailDistance) + //
                "/" + //
                decimalFormat.format(longest) + //
                ")-100%");
        this.tailRetraction = percentInstance.format(tailRetraction);
    }

    void validateLayout() {
        this.dynPanel.validate();
        this.dynButtonPanel.validate();
        this.dynTxtPanel.validate();
        this.statButtonPanel.validate();
        validate();
        pack();
    }
}
