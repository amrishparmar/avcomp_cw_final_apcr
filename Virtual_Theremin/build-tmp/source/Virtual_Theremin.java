import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import processing.video.*; 
import javax.sound.sampled.AudioFormat; 
import javax.sound.sampled.AudioSystem; 
import javax.sound.sampled.DataLine; 
import javax.sound.sampled.LineUnavailableException; 
import javax.sound.sampled.SourceDataLine; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class Virtual_Theremin extends PApplet {

  


Capture cam;

AudioThread audioThread;

PVector ampPos;
PVector freqPos;

float amp;
float freq;
float phase;

float up, down, left, right;

public void setup() {
  size(640, 480);

  String[] cameras = Capture.list();
  
  if (cameras.length == 0) {
    println("There are no cameras available for capture.");
    exit();
  } 
  else {
    println("Available cameras:");
    for (int i = 0; i < cameras.length; i++) {
      println(cameras[i]);
    }

    //cam = new Capture(this, cameras[0]);
    cam = new Capture(this, 640, 480, 30);
    cam.start();     
  }

  ampPos = new PVector(width/2, height - 50);
  freqPos = new PVector(width - 50, height/2);

  freq = freqPos.y;
  phase = 0;
  audioThread = new AudioThread();
  audioThread.start();      
}

public void draw() {
  if (cam.available() == true) {
    cam.read();
  }
  image(cam, 0, 0);
  
  drawStats();
  drawTrackers();
  moveTrackers();

  freq = abs(freqPos.y - height);
  amp = ampPos.x;
}

public void drawStats() {
  // fps count
  fill(0);
  textSize(20);
  text("FPS: "+frameRate, 10, 20);

  // x pos
  text("X: "+ampPos.x, 10, 40);
  // y pos
  text("Y: "+freqPos.y, 10, 60);
}

public void drawTrackers() {
  // mouse pos
  fill(255, 255, 0);
  ellipse(mouseX, mouseY, 20, 20);

  // x pos
  fill(255, 0, 0);
  stroke(0);
  strokeWeight(5);
  ellipse(ampPos.x, ampPos.y, 40, 40);

  // y pos
  fill(255, 0, 0);
  stroke(0);
  strokeWeight(5);
  ellipse(freqPos.x, freqPos.y, 40, 40);
}

public void moveTrackers() {
  ampPos.x += (right - left) * 5;
  freqPos.y += (down - up) * 5;

  ampPos.x = constrain(ampPos.x, 20, width - 20);
  freqPos.y = constrain(freqPos.y, 20, height - 20);
}

// this function gets called when you press the escape key in the sketch
public void stop(){
  // tell the audio to stop
  audioThread.quit();
  // call the version of stop defined in our parent class, in case it does anything vital
  super.stop();
}

// this gets called by the audio thread when it wants some audio
// we should fill the sent buffer with the audio we want to send to the 
// audio output
public void generateAudioOut(float[] buffer){
  for (int i = 0; i < buffer.length; i++){
    buffer[i] = 0;
    // generate white noise
    for (float partial = 0; partial < 10; partial++){
      buffer[i] += sin(TWO_PI / 44100 * phase * freq);
      buffer[i] *= 5;
      phase = (phase + 1) % 44100;      
    }
  }
}

public void keyPressed() {
  if (key == CODED) {
    if (keyCode == LEFT) {
      left = 1; 
    }
    if (keyCode == RIGHT) {
      right = 1;
    }
    if (keyCode == UP) {
      up = 1;
    }
    if (keyCode == DOWN) {
      down = 1;
    }
  }
}

public void keyReleased() {
  if (key == CODED) {
    if (keyCode == LEFT) {
      left = 0;
    }
    if (keyCode == RIGHT) {
      right = 0;
    }
    if (keyCode == UP) {
      up = 0;
    }
    if (keyCode == DOWN) {
      down = 0;
    }
  }
}
/*
 *  This file has been adapted from 
 * https://github.com/mhroth/jvsthost/blob/master/src/com/synthbot/audioio/vst/JVstAudioThread.java
 *
 *  which contains the following license:
 *
 * Copyright 2007 - 2009 Martin Roth (mhroth@gmail.com)
 *                        Matthew Yee-King
 * 
 *  JVstHost is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  JVstHost is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with JVstHost.  If not, see <http://www.gnu.org/licenses/>.
 *
 */







class AudioThread extends Thread {
  // buffer to store the audio data coming in
  // it's a 2D array as we may have more than one channel
  private  float[][] fInputs;
  // buffer to store the audio data going out
  private  float[][] fOutputs;
  // raw binary buffer which is used to actually send data to the sound card as bytes
  private  byte[] bOutput;
  // how many samples to process per cycle? 
  private int blockSize;
  // how many audio outputs/ inputs
  private int numOutputs;
  private int numInputs;
  // samples per second
  private int sampleRate;
  private int bitDepth;
  // the type of audio we are going to generate (PCM/compressed etc? )
  // see http://download.oracle.com/javase/1.5.0/docs/api/javax/sound/sampled/AudioFormat.html
  private AudioFormat audioFormat;
  //  used to access the audio system so we can send data to and from it
  private SourceDataLine sourceDataLine;
  // are we running?
  private boolean running;
  
  // we pull this value into a variable for speed (avoids repeating the field access and cast operation)
  private static final float ShortMaxValueAsFloat = (float) Short.MAX_VALUE;

  // constructor attempts to initialise the audio device
  AudioThread (){
    running = false;
   // mono
     numOutputs = 1;
     numInputs = 1;
     // block size 4096 samples, lower it if you want lower latency
    blockSize = 4096;
    sampleRate = 44100;
    bitDepth = 16;
    // initialise audio buffers
    fInputs = new float[numInputs][blockSize];
    fOutputs = new float[numOutputs][blockSize];
    bOutput = new byte[numOutputs * blockSize * 2];
  // set up the audio format, 
    audioFormat = new AudioFormat(sampleRate, bitDepth, numOutputs, true, false);
    DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);

    sourceDataLine = null;
  // here we try to initialise the audio system. try catch is exception handling, i.e. 
  // dealing with things not working as expected
    try {
      sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
      sourceDataLine.open(audioFormat, bOutput.length);
      sourceDataLine.start();
      running = true;
    } catch (LineUnavailableException lue) {
      // it went wrong!
      lue.printStackTrace(System.err);
      System.out.println("Could not initialise audio. check above stack trace for more info");
      //System.exit(1);
    }
  }
  // we are ovverriding the run method from the Thread class
  // run gets called when the thread starts
  public @Override
  // We must implement run, this gets triggered by start()
  void run () {
    while (running) {
      // generate the float buffer
      generateAudioOut(fOutputs[0]);
      // convert to bytes and send it to the card
      sourceDataLine.write(floatsToBytes(fOutputs, bOutput), 0, bOutput.length);
    }
  }

  // returns the current contents of the audio buffer
  public float[] getAudio(){
    return fOutputs[0];
  }
  
  // Our method that quits the thread
  // taken from http://wiki.processing.org/w/Threading
  public void quit() {
    System.out.println("Quitting audio thread."); 
    running = false;  // Setting running to false ends the loop in run()
    sourceDataLine.drain();
    sourceDataLine.close();  
    // IUn case the thread is waiting. . .
    // note that the interrupt method is defined in the Thread class which we are extending
    interrupt();
  }
  
   /**
   * Converts a float audio array [-1,1] to an interleaved array of 16-bit samples
   * in little-endian (low-byte, high-byte) format.
   */
  private byte[] floatsToBytes(float[][] fData, byte[] bData) {
    int index = 0;
    for (int i = 0; i < blockSize; i++) {
      for (int j = 0; j < numOutputs; j++) {
        short sval = (short) (fData[j][i] * ShortMaxValueAsFloat);
        bData[index++] = (byte) (sval & 0x00FF);
        bData[index++] = (byte) ((sval & 0xFF00) >> 8);
      }
    }
    return bData;
  }
  
  
}
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "Virtual_Theremin" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
