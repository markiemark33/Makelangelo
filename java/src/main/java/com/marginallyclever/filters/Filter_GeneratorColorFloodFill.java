package com.marginallyclever.filters;


import com.marginallyclever.makelangelo.C3;
import com.marginallyclever.makelangelo.ColorPalette;
import com.marginallyclever.makelangelo.MachineConfiguration;
import com.marginallyclever.makelangelo.MainGUI;
import com.marginallyclever.makelangelo.MultilingualSupport;
import com.marginallyclever.makelangelo.Point2D;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 
 * @author danroyer
 * @since at least 7.1.4
 */
public class Filter_GeneratorColorFloodFill extends Filter {
	ColorPalette palette;	
	int diameter=0;
	int last_x,last_y;
	BufferedImage imgChanged;
	BufferedImage imgMask;
	OutputStreamWriter osw;
	
	
	public Filter_GeneratorColorFloodFill(MainGUI gui, MachineConfiguration mc,
			MultilingualSupport ms) {
		super(gui, mc, ms);
		
		palette = new ColorPalette();
		palette.addColor(new C3(0,0,0));
		palette.addColor(new C3(255,0,0));
		palette.addColor(new C3(0,255,0));
		palette.addColor(new C3(0,0,255));
		palette.addColor(new C3(255,255,255));
	}

	public String getName() { return translator.get("RGBFloodFillName"); }


	/**
	 * Overrides MoveTo() because optimizing for zigzag is different logic than straight lines.
	 */
	protected void moveTo(float x,float y,boolean up) throws IOException {
		if(lastup!=up) {
			if(up) liftPen(osw);
			else   lowerPen(osw);
			lastup=up;
		}
		tool.writeMoveTo(osw, TX(x), TY(y));
	}
	
	/**
	 * test the mask from x0,y0 (top left) to x1,y1 (bottom right) to see if this region has already been visited
	 * @param x0 left
	 * @param y0 top
	 * @param x1 right
	 * @param y1 bottom
	 * @return true if all the pixels in this region are zero.
	 */
	protected boolean getMaskTouched(int x0,int y0) {
		int x1 = x0 + diameter;
		int y1 = y0 + diameter;
		if(x0<0) x0=0;
		if(x1>image_width-1) x1 = image_width-1;
		if(y0<0) y0=0;
		if(y1>image_height-1) y1 = image_height-1;

		Color value;
		int sum=0;
		for(int y=y0;y<y1;++y) {
			for(int x=x0;x<x1;++x) {
				++sum;
				value = new Color(imgMask.getRGB(x, y));
				if(value.getRed()!=0) {
					return true;
				}
			}
		}

		return (sum==0);
	}

	protected void setMaskTouched(int x0,int y0,int x1,int y1) {
		if(x0<0) x0=0;
		if(x1>image_width-1) x1 = image_width-1;
		if(y0<0) y0=0;
		if(y1>image_height-1) y1 = image_height-1;

		int c = (new C3(255,255,255)).toInt();
		for(int y=y0;y<y1;++y) {
			for(int x=x0;x<x1;++x) {
				imgMask.setRGB(x, y, c);
			}
		}
		//imgMask.flush();
	}
	
	
	/**
	 * sample the pixels from x0,y0 (top left) to x1,y1 (bottom right) and average the color.
	 * @param x0
	 * @param y0
	 * @param x1
	 * @param y1
	 * @return the average color in the region.  if nothing is sampled, return white.
	 */
	protected C3 takeImageSampleBlock(int x0,int y0,int x1,int y1) {
		// point sampling
		C3 value = new C3(0,0,0);
		int sum=0;
		
		if(x0<0) x0=0;
		if(x1>image_width-1) x1 = image_width-1;
		if(y0<0) y0=0;
		if(y1>image_height-1) y1 = image_height-1;

		for(int y=y0;y<y1;++y) {
			for(int x=x0;x<x1;++x) {
				value.add(new C3(imgChanged.getRGB(x, y)));
				++sum;
			}
		}

		if(sum==0) return new C3(255,255,255);
		
		return value.mul(1.0f/sum);
	}


	protected boolean doesQuantizedBlockMatch(int color_index,float x,float y) {
		C3 original_color = takeImageSampleBlock((int)x, (int)y, (int)(x+diameter), (int)(y+diameter));
		int quantized_color = palette.quantizeIndex(original_color);
		return ( quantized_color == color_index );
	}
	
	
	/**
	 * queue-based flood fill 
	 * @param color_index
	 * @param img
	 * @param out
	 * @throws IOException
	 */
	protected void floodFillBlob(int color_index,int x,int y) throws IOException {
		Queue<Point2D> points_to_visit = new LinkedList<Point2D>();
		points_to_visit.add(new Point2D(x,y));
		
		Point2D a;

		while(!points_to_visit.isEmpty()) {
			a = points_to_visit.remove();

			if( getMaskTouched((int)a.x, (int)a.y) ) continue;
			if( !doesQuantizedBlockMatch(color_index, a.x,a.y) ) continue;
			// mark this spot as visited.
			setMaskTouched((int)a.x, (int)a.y, (int)(a.x+diameter), (int)(a.y+diameter));

			// if the difference between the last filled pixel and this one is more than diameter*2, pen up, move, pen down.
			float dx=(float)(a.x-last_x);
			float dy=(float)(a.y-last_y);
			if((dx*dx+dy*dy) > diameter*diameter*2.0f)
			{
				//System.out.print("Jump at "+x+", "+y+"\n");
				moveTo(last_x, last_y, true);
				moveTo(a.x, a.y, true);
				moveTo(a.x, a.y, false);
			} else {
				//System.out.print("Move to "+x+", "+y+"\n");
				moveTo(a.x, a.y, false);
			}
			// update the last position.
			last_x=(int)a.x;
			last_y=(int)a.y;

//			if( !getMaskTouched((int)(a.x+diameter),(int)a.y           ) )
				points_to_visit.add(new Point2D(a.x+diameter,a.y         ));
//			if( !getMaskTouched((int)(a.x-diameter),(int)a.y           ) )
				points_to_visit.add(new Point2D(a.x-diameter,a.y         ));
//			if( !getMaskTouched((int)a.x           ,(int)(a.y+diameter)) )
				points_to_visit.add(new Point2D(a.x         ,a.y+diameter));
//			if( !getMaskTouched((int)a.x           ,(int)(a.y-diameter)) )
				points_to_visit.add(new Point2D(a.x         ,a.y-diameter));
		}
	}

	
	/**
	 * find blobs of color in the original image.  Send that to the flood fill system.
	 * @param color_index index into the list of colors at the top of the class
	 * @param img source bufferedimage
	 * @param out output stream for writing gcode.
	 * @throws IOException
	 */
	void scanForContiguousBlocks(int color_index) throws IOException {
		C3 original_color;
		int quantized_color;
		
		int x,y;
		int z=0;

		mainGUI.log("<font color='orange'>Palette color "+palette.getColor(color_index).toString()+"</font>\n");
		
		for(y=0;y<image_height;y+=diameter) {
			for(x=0;x<image_width;x+=diameter) {
				if( getMaskTouched(x,y) ) continue;
				
				original_color = takeImageSampleBlock(x, y, x+diameter, y+diameter);
				quantized_color = palette.quantizeIndex(original_color); 
				if( quantized_color == color_index ) {
					// found blob
					floodFillBlob(color_index,x,y);
					z++;
					//if(z==20)
//						return;
				}
			}
		}
		System.out.println("Found "+z+" blobs.");
	}
	
	private void scanColor(int i) throws IOException {
		// "please change to tool X and press any key to continue"
		tool = machine.getTool(i);
		tool.writeChangeTo(osw);
		// Make sure the pen is up for the first move
		liftPen(osw);
		
		mainGUI.log("<font color='green'>Color "+i+"</font>\n");
		
		scanForContiguousBlocks(i);
	}
	
	/**
	 * create horizontal lines across the image.  Raise and lower the pen to darken the appropriate areas
	 * @param img the image to convert.
	 */
	public void convert(BufferedImage img) throws IOException {
		// The picture might be in color.  Smash it to 255 shades of grey.
		//Filter_DitherFloydSteinbergRGB bw = new Filter_DitherFloydSteinbergRGB(mainGUI,machine,translator);
		//img = bw.process(img);
		
		// create a color mask so we don't repeat any pixels
		imgMask = new BufferedImage(img.getWidth(),img.getHeight(),BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imgMask.createGraphics();
		g.setPaint ( new Color(0,0,0) );
		g.fillRect ( 0, 0, imgMask.getWidth(), imgMask.getHeight() );
		
		
		// Open the destination file
		osw = new OutputStreamWriter(new FileOutputStream(dest),"UTF-8");
		// Set up the conversion from image space to paper space, select the current tool, etc.
		imageStart(img,osw);


		float pw = (float)machine.getPaperWidth();
		float df = tool.getDiameter() * (float)img.getWidth() / (4.0f*pw);
		if(df<1) df=1;

//		float steps = img.getWidth() / df;
		
		//System.out.println("Diameter = "+df);
		//System.out.println("Steps = "+steps);
		
		diameter = (int)df;
		
		imgChanged=img;

		last_x=img.getWidth()/2;
		last_y=img.getHeight()/2;
		
		scanColor(0);  // black
		scanColor(1);  // red
		scanColor(2);  // green
		scanColor(3);  // blue
		
		mainGUI.log("<font color='green'>Signing my name</font>\n");
		
		// pen already lifted
		signName(osw);
		moveTo(0, 0, true);
		
		// close the file
		osw.close();
		/*
		try {
		    // save image
		    File outputfile = new File("saved.png");
		    ImageIO.write(img, "png", outputfile);
		} catch (IOException e) {
		    e.printStackTrace();
		}
		*/
	}
}


/**
 * This file is part of DrawbotGUI.
 *
 * DrawbotGUI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * DrawbotGUI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */