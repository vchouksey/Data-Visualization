/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import gui.RaycastRendererPanel;
import gui.TransferFunction2DEditor;
import gui.TransferFunctionEditor;
import java.awt.image.BufferedImage;
import util.TFChangeListener;
import util.VectorMath;
import volume.GradientVolume;
import volume.Volume;
import java.util.ArrayList;


/**
 *
 * @author michel
 */
public class RaycastRenderer extends Renderer implements TFChangeListener {

    private Volume volume = null;
    private GradientVolume gradients = null;
    private Visualization vis = null;
    private int blurredres;
    int mode = 0;
    private int stepsize = 2;
    RaycastRendererPanel panel;
    TransferFunction tFunc;
    TransferFunctionEditor tfEditor;
    TransferFunction2DEditor tfEditor2D;
    
    public RaycastRenderer() {
        panel = new RaycastRendererPanel(this);
        panel.setSpeedLabel("0");
    }

    public void setVolume(Volume vol) {
        System.out.println("Assigning volume");
        volume = vol;

        System.out.println("Computing gradients");
        gradients = new GradientVolume(vol);

        // set up image for storing the resulting rendering
        // the image width and height are equal to the length of the volume diagonal
        int imageSize = (int) Math.floor(Math.sqrt(vol.getDimX() * vol.getDimX() + vol.getDimY() * vol.getDimY()
                + vol.getDimZ() * vol.getDimZ()));
        if (imageSize % 2 != 0) {
            imageSize = imageSize + 1;
        }
        image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        // create a standard TF where lowest intensity maps to black, the highest to white, and opacity increases
        // linearly from 0.0 to 1.0 over the intensity range
        tFunc = new TransferFunction(volume.getMinimum(), volume.getMaximum());
        
        // uncomment this to initialize the TF with good starting values for the orange dataset 
        //tFunc.setTestFunc();
        
        
        tFunc.addTFChangeListener(this);
        tfEditor = new TransferFunctionEditor(tFunc, volume.getHistogram());
        
        tfEditor2D = new TransferFunction2DEditor(volume, gradients);
        tfEditor2D.addTFChangeListener(this);

        System.out.println("Finished initialization of RaycastRenderer");
    }

    public RaycastRendererPanel getPanel() {
        return panel;
    }

    public TransferFunction2DEditor getTF2DPanel() {
        return tfEditor2D;
    }
    
    public TransferFunctionEditor getTFPanel() {
        return tfEditor;
    }
     

    short getVoxel(double[] coord) {

        if (coord[0] < 0 || coord[0] > volume.getDimX() || coord[1] < 0 || coord[1] > volume.getDimY()
                || coord[2] < 0 || coord[2] > volume.getDimZ()) {
            return 0;
        }

        int x = (int) Math.floor(coord[0]);
        int y = (int) Math.floor(coord[1]);
        int z = (int) Math.floor(coord[2]);

        return volume.getVoxel(x, y, z);
    }


    void slicer(double[] viewMatrix) {

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();
       
        
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                        + volumeCenter[0];
                pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                        + volumeCenter[1];
                pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                        + volumeCenter[2];
             
                int val = getVoxel(pixelCoord);
                // Map the intensity to a grey value by linear scaling
                voxelColor.r = val/max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                // Alternatively, apply the transfer function to obtain a color
                //voxelColor = tFunc.getColor(val);
                
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }

    }
    
    public short trilinearInterpol(double[] coord){
        double[] vectxyz = new double[8];
        double alpha = 0;
        double beta = 0;
        double gamma = 0;
        if(  (0<=Math.floor(coord[0]) && Math.ceil(coord[0]) < volume.getDimX())  && //check [0,volDimX]
             (0<=Math.floor(coord[1]) && Math.ceil(coord[1]) < volume.getDimY())  && //check [0,volDimY]
             (0<=Math.floor(coord[2]) && Math.ceil(coord[2]) < volume.getDimZ())){     //check [0,volDimZ]
        
            vectxyz[0] = volume.getVoxel((int)Math.floor(coord[0]) , (int)Math.floor(coord[1]), (int)Math.floor(coord[2])); //V000
            vectxyz[1] = volume.getVoxel((int)Math.ceil(coord[0]) , (int)Math.floor(coord[1]), (int)Math.floor(coord[2])); //V100
            vectxyz[2] = volume.getVoxel((int)Math.floor(coord[0]) , (int)Math.ceil(coord[1]), (int)Math.floor(coord[2])); //V010
            vectxyz[3] = volume.getVoxel((int)Math.ceil(coord[0]) , (int)Math.ceil(coord[1]), (int)Math.floor(coord[2])); //V110
            vectxyz[4] = volume.getVoxel((int)Math.floor(coord[0]) , (int)Math.floor(coord[1]), (int)Math.ceil(coord[2])); //V001
            vectxyz[5] = volume.getVoxel((int)Math.ceil(coord[0]) , (int)Math.floor(coord[1]), (int)Math.ceil(coord[2])); //V101
            vectxyz[6] = volume.getVoxel((int)Math.floor(coord[0]) , (int)Math.ceil(coord[1]), (int)Math.ceil(coord[2])); //V011
            vectxyz[7] = volume.getVoxel((int)Math.ceil(coord[0]) , (int)Math.ceil(coord[1]), (int)Math.ceil(coord[2])); //V111
            //compute alpha beta and gamma
            alpha = coord[0] - (int)Math.floor(coord[0]);
            beta = coord[1] - (int)Math.floor(coord[1]);
            gamma = coord[2] - (int)Math.floor(coord[2]);

            return (short) ((1-alpha)*(1-beta)*(1-gamma)*vectxyz[0] //Value at position (x,y,z) within the cube
                            + alpha*(1-beta)*(1-gamma)*vectxyz[1]
                            + (1-alpha)*beta*(1-gamma)*vectxyz[2] 
                            + alpha*beta*(1 - gamma)*vectxyz[3]
                            + (1-alpha)*(1-beta)*gamma*vectxyz[4]
                            + alpha*(1-beta)*gamma*vectxyz[5]
                            + (1-alpha)*beta*gamma*vectxyz[6] 
                            + alpha*beta*gamma*vectxyz[7]
                           );  
            
        }else {
            return 0;
        }
    }
    void MIP(double[] viewMatrix){
        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }
        this.setRes(interactiveMode);
        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        //int maxsize = (Math.max(volume.getDimX(),Math.max(volume.getDimY(), volume.getDimZ())))/2;
        //System.out.println("maxsize" + maxsize);
        double max = volume.getMaximum();
        double maxdistance = (Math.max(volume.getDimX(),Math.max(volume.getDimY(), volume.getDimZ())))/2.0;
        TFColor voxelColor = new TFColor();
        
        for(int j=0 ;j < image.getHeight();j+= this.blurredres){
            for(int i=0; i < image.getWidth();i+= this.blurredres){
                int maxval = 0;
                
                for(double k=-maxdistance ;k < maxdistance ;k += stepsize){
                 pixelCoord[0] = uVec[0] *(i - imageCenter) + vVec[0] * (j - imageCenter)+ volumeCenter[0] + k * viewVec[0];
                 pixelCoord[1] = uVec[1] *(i - imageCenter) + vVec[1] * (j - imageCenter)+ volumeCenter[1] + k * viewVec[1];
                 pixelCoord[2] = uVec[2] *(i - imageCenter) + vVec[2] * (j - imageCenter)+ volumeCenter[2] + k * viewVec[2];
                 
                 int value = this.trilinearInterpol(pixelCoord);
                 //int value = this.getVoxel(pixelCoord);
                 if(value > maxval){
                     maxval = value;
                 }
            }
            // Map the intensity to a grey value by linear scaling
            voxelColor.r = maxval/max;
            voxelColor.g = voxelColor.r;
            voxelColor.b = voxelColor.r;
            voxelColor.a = maxval > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
            
            
            // BufferedImage expects a pixel color packed as ARGB in an int
            int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
            int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
            int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
            int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
            int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
            image.setRGB(i, j, pixelColor);
            
            if (interactiveMode){
                image.setRGB(i + 1,j,pixelColor);
                image.setRGB(i,j + 1,pixelColor);
                image.setRGB(i+1, j + 1, pixelColor);
            }
            }
        }
        
    }
    void composite(double[] viewMatrix){
        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }
        this.setRes(interactiveMode);
        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);
        
        TFColor voxelColor = new TFColor();
        int max = volume.getMaximum();
        double maxdistance = Math.max(volume.getDimX(),Math.max(volume.getDimY(), volume.getDimZ()));
        //create arraylist of colors for the composite function
        ArrayList<TFColor> colors = new ArrayList<TFColor>();
       //loop over image
       for (int i=0; i <image.getHeight(); i += this.blurredres){
           for (int j = 0 ; j < image.getWidth(); j += this.blurredres){    
               colors.clear();

                
                for(double k=-maxdistance;k < maxdistance ;k += this.blurredres){
                    pixelCoord[0] = uVec[0] *(i - imageCenter) + vVec[0] * (j - imageCenter)+ volumeCenter[0] + k * viewVec[0];
                    pixelCoord[1] = uVec[1] *(i - imageCenter) + vVec[1] * (j - imageCenter)+ volumeCenter[1] + k * viewVec[1];
                    pixelCoord[2] = uVec[2] *(i - imageCenter) + vVec[2] * (j - imageCenter)+ volumeCenter[2] + k * viewVec[2];
                    
                    //interpolate
                    int val = this.trilinearInterpol(pixelCoord);
                    
                    TFColor intermediateColor = new TFColor();
                    
                    intermediateColor.r = tFunc.getColor(val).r;
                    intermediateColor.g = tFunc.getColor(val).g;
                    intermediateColor.b = tFunc.getColor(val).b;
                    intermediateColor.a = tFunc.getColor(val).a;
                    
                    colors.add(intermediateColor);                   
                }   
            double ru = 0;
            double gu = 0;
            double bu = 0;
            double au = 0;
            
            for (int p=0; p < colors.size();p++){
                double AU = colors.get(p).a;
                
                if (AU > 0){
                    double RU = colors.get(p).r;
                    double GU = colors.get(p).g;
                    double BU = colors.get(p).b;
                    ru += AU *RU * (1-au);
                    gu += GU *RU * (1-au);
                    bu += BU *RU * (1-au);
                    au += AU * (1-au);
                }
            }
            
            voxelColor.a = au;
            voxelColor.g = gu;
            voxelColor.b = bu;
            voxelColor.r = ru;
            // BufferedImage expects a pixel color packed as ARGB in an int
            int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
            int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
            int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
            int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
            int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
            image.setRGB(i, j, pixelColor);
            if(interactiveMode){
                image.setRGB(i+1,j,pixelColor);
                image.setRGB(i,j+1,pixelColor);
                image.setRGB(i+1,j+1,pixelColor);
            }
           }
       }
    }
    
    private void drawBoundingBox(GL2 gl) {
        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);
        gl.glLineWidth(1.5f);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopAttrib();

    }
    
    public void setMode(int j){
        this.mode = j;
        vis.update();
    }
    public void setVis(Visualization vis){
        this.vis = vis;
    }
    
    public Visualization getVis() {
        return this.vis;
    }
    
    public void setRes (boolean blurring){
        if (blurring){
            this.blurredres = 2;
        }else {
            blurredres = 1;
        }
    }
    
    @Override
    public void visualize(GL2 gl) {


        if (volume == null) {
            return;
        }

        drawBoundingBox(gl);

        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, viewMatrix, 0);

        long startTime = System.currentTimeMillis();
        
        switch(mode){
            case 0: slicer(viewMatrix);
                    break;
            case 1: MIP(viewMatrix);
                break;
            case 2: composite(viewMatrix);
                break;
            default: slicer(viewMatrix);
                    break;
        }
        
        long endTime = System.currentTimeMillis();
        double runningTime = (endTime - startTime);
        panel.setSpeedLabel(Double.toString(runningTime));

        Texture texture = AWTTextureIO.newTexture(gl.getGLProfile(), image, false);

        gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        // draw rendered image as a billboard texture
        texture.enable(gl);
        texture.bind(gl);
        double halfWidth = image.getWidth() / 2.0;
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2d(0.0, 0.0);
        gl.glVertex3d(-halfWidth, -halfWidth, 0.0);
        gl.glTexCoord2d(0.0, 1.0);
        gl.glVertex3d(-halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 1.0);
        gl.glVertex3d(halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 0.0);
        gl.glVertex3d(halfWidth, -halfWidth, 0.0);
        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);
        gl.glPopMatrix();

        gl.glPopAttrib();


        if (gl.glGetError() > 0) {
            System.out.println("some OpenGL error: " + gl.glGetError());
        }

    }
    private BufferedImage image;
    private double[] viewMatrix = new double[4 * 4];

    @Override
    public void changed() {
        for (int i=0; i < listeners.size(); i++) {
            listeners.get(i).changed();
        }
    }
}
