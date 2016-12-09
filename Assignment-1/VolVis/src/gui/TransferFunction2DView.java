/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 *
 * @author michel
 */
public class TransferFunction2DView extends javax.swing.JPanel {

    TransferFunction2DEditor ed;
    private final int DOTSIZE = 8;
    public Ellipse2D.Double baseControlPoint, radiusControlPoint;
    boolean selectedBaseControlPoint, selectedRadiusControlPoint;
    private double maxHistoMagnitude;
    
    //improved trianglewidget
    public Ellipse2D.Double minControlPoint;
    public Ellipse2D.Double maxControlPoint;
    boolean selectedCPMin;
    boolean selectedCPMax;
    
    /**
     * Creates new form TransferFunction2DView
     * @param ed
     */
    public TransferFunction2DView(TransferFunction2DEditor ed) {
        initComponents();
        
        this.ed = ed;
        selectedBaseControlPoint = false;
        selectedRadiusControlPoint = false;
        selectedCPMin = false;
        selectedCPMax = true;
        addMouseMotionListener(new TriangleWidgetHandler());
        addMouseListener(new SelectionHandler());
    }
    
    @Override
    public void paintComponent(Graphics g) {

        Graphics2D g2 = (Graphics2D) g;

        int w = this.getWidth();
        int h = this.getHeight();
        g2.setColor(Color.white);
        g2.fillRect(0, 0, w, h);
        
        maxHistoMagnitude = ed.histogram[0];
        for (int i = 0; i < ed.histogram.length; i++) {
            maxHistoMagnitude = ed.histogram[i] > maxHistoMagnitude ? ed.histogram[i] : maxHistoMagnitude;
        }
        
        double binWidth = (double) w / (double) ed.xbins;
        double binHeight = (double) h / (double) ed.ybins;
        maxHistoMagnitude = Math.log(maxHistoMagnitude);
        
        for (int y = 0; y < ed.ybins; y++) {
            for (int x = 0; x < ed.xbins; x++) {
                if (ed.histogram[y * ed.xbins + x] > 0) {
                    int intensity = (int) Math.floor(255 * (1.0 - Math.log(ed.histogram[y * ed.xbins + x]) / maxHistoMagnitude));
                    g2.setColor(new Color(intensity, intensity, intensity));
                    g2.fill(new Rectangle2D.Double(x * binWidth, h - (y * binHeight), binWidth, binHeight));
                }
            }
        }
        
        int ypos = h;
        int xpos = (int) (ed.triangleWidget.baseIntensity * binWidth);
        g2.setColor(Color.black);
        baseControlPoint = new Ellipse2D.Double(xpos - DOTSIZE / 2, ypos - DOTSIZE, DOTSIZE, DOTSIZE);
        g2.fill(baseControlPoint);
        g2.drawLine(xpos, ypos, xpos - (int) (ed.triangleWidget.radius * binWidth * ed.maxGradientMagnitude), 0);
        g2.drawLine(xpos, ypos, xpos + (int) (ed.triangleWidget.radius * binWidth * ed.maxGradientMagnitude), 0);
        radiusControlPoint = new Ellipse2D.Double(xpos + (ed.triangleWidget.radius * binWidth * ed.maxGradientMagnitude) - DOTSIZE / 2,  0, DOTSIZE, DOTSIZE);
        g2.fill(radiusControlPoint);
        
        //extended triangle widget
        
        int minY = h - (int) (ed.triangleWidget.minGrad/ed.maxGradientMagnitude * ed.ybins * binHeight);
        int maxY = h - (int) (ed.triangleWidget.maxGrad/ed.maxGradientMagnitude * ed.ybins * binHeight);
        g2.setColor(Color.blue);
        maxControlPoint = new Ellipse2D.Double(maxY >= DOTSIZE ? xpos - 10 - DOTSIZE / 2 : xpos - DOTSIZE / 2, maxY - DOTSIZE / 2, DOTSIZE, DOTSIZE);
        minControlPoint = new Ellipse2D.Double(xpos - DOTSIZE / 2, minY - DOTSIZE / 2, DOTSIZE, DOTSIZE);;
        g2.fill(minControlPoint);
        g2.fill(maxControlPoint);
         // lowControlPoint	
        int minXLeft = xpos - (int) (ed.triangleWidget.radius * binWidth * ed.triangleWidget.minGrad);
        int minXRight = xpos + (int) (ed.triangleWidget.radius * binWidth * ed.triangleWidget.minGrad);
        g2.drawLine(minXLeft, minY, minXRight, minY);
        // upControlPoint
        int maxXleft = xpos - (int) (ed.triangleWidget.radius * binWidth * ed.triangleWidget.maxGrad);
        int maxXRight = xpos + (int) (ed.triangleWidget.radius * binWidth * ed.triangleWidget.maxGrad);
        g2.drawLine(maxXleft, maxY, maxXRight, maxY);
    }
    
    
    private class TriangleWidgetHandler extends MouseMotionAdapter {

        @Override
        public void mouseMoved(MouseEvent e) {
            if (baseControlPoint.contains(e.getPoint()) || radiusControlPoint.contains(e.getPoint()) || minControlPoint.contains(e.getPoint()) || maxControlPoint.contains(e.getPoint())) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
        }
        
        @Override
        public void mouseDragged(MouseEvent e) {
            if (selectedBaseControlPoint || selectedRadiusControlPoint || selectedCPMin || selectedCPMax) {
                Point dragEnd = e.getPoint();
                double h = getHeight();
                if (selectedBaseControlPoint) {
                    // restrain to horizontal movement
                    dragEnd.setLocation(dragEnd.x, baseControlPoint.getCenterY());
                } else if (selectedRadiusControlPoint) {
                    // restrain to horizontal movement and avoid radius getting 0
                    dragEnd.setLocation(dragEnd.x, radiusControlPoint.getCenterY());
                    if (dragEnd.x - baseControlPoint.getCenterX() <= 0) {
                        dragEnd.x = (int) (baseControlPoint.getCenterX() + 1);
                    }
                }else if (selectedCPMin){
                    if (dragEnd.y > h){
                        dragEnd.y = (int) h;
                    }
                    if (dragEnd.y < 0){
                        dragEnd.y = 0;
                    }
                    dragEnd.setLocation(maxControlPoint.getCenterX(),dragEnd.y);
                }else if (selectedCPMax){
                    if (dragEnd.y < 0){
                        dragEnd.y = 0;
                    }else if (dragEnd.y > h){
                        dragEnd.y = (int) h;
                    }
                    dragEnd.setLocation(minControlPoint.getCenterX(),dragEnd.y);
                }
                if (dragEnd.x < 0) {
                    dragEnd.x = 0;
                }
                if (dragEnd.x >= getWidth()) {
                    dragEnd.x = getWidth() - 1;
                }
                double w = getWidth();
                
                double binWidth = (double) w / (double) ed.xbins;
                if (selectedBaseControlPoint) {
                    ed.triangleWidget.baseIntensity = (short) (dragEnd.x / binWidth);
                } else if (selectedRadiusControlPoint) {
                    ed.triangleWidget.radius = (dragEnd.x - (ed.triangleWidget.baseIntensity * binWidth))/(binWidth*ed.maxGradientMagnitude);
                }else if (selectedCPMax){
                    double newMaxGrad = ed.maxGradientMagnitude * (h - dragEnd.y) / h;
                    if (newMaxGrad < ed.triangleWidget.minGrad){
                        ed.triangleWidget.maxGrad = ed.triangleWidget.minGrad;
                        ed.triangleWidget.minGrad = newMaxGrad;
                    }else{
                        ed.triangleWidget.maxGrad = newMaxGrad;
                    }
                }else if (selectedCPMin){
                    double newMinGrad = ed.maxGradientMagnitude * (h-dragEnd.y) / h;
                    if (newMinGrad > ed.triangleWidget.maxGrad){
                        ed.triangleWidget.minGrad = ed.triangleWidget.maxGrad;
                        ed.triangleWidget.maxGrad = newMinGrad;
                        
                    }else{
                        ed.triangleWidget.minGrad = newMinGrad;
                    }
                }
                ed.setSelectedInfo();
                
                repaint();
            } 
        }

    }
    
    
    private class SelectionHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (baseControlPoint.contains(e.getPoint())) {
                selectedBaseControlPoint = true;
            } else if (radiusControlPoint.contains(e.getPoint())) {
                selectedRadiusControlPoint = true;
            } else {
                selectedRadiusControlPoint = false;
                selectedBaseControlPoint = false;
            }
            
            if (maxControlPoint.contains(e.getPoint())){
                selectedCPMax = true;
                System.out.println("maxCP selected");
            }else if(minControlPoint.contains(e.getPoint())){
                selectedCPMin = true;
                System.out.println("MinCP selected");
            }else{
                selectedCPMax = false;
                selectedCPMin = false;
            }
        }
        
        @Override
        public void mouseReleased(MouseEvent e) {
            selectedRadiusControlPoint = false;
            selectedBaseControlPoint = false;
            selectedCPMin = false;
            selectedCPMax = false;
            ed.changed();
            repaint();
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
