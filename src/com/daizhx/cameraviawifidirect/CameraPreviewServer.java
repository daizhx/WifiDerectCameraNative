package com.daizhx.cameraviawifidirect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Pattern;

import android.app.Service;
import android.content.Intent;
import android.hardware.Camera;
import android.os.IBinder;
import android.util.Log;

public class CameraPreviewServer extends Service implements Camera.PreviewCallback{
	public static final String TAG = "ipcamera";
	private Camera mCamera = null;
	private int preview_width, preview_height;
	private static final int mPort = 8988;
	private ServerSocket mServerSocket = null;
	
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreate() {
		
		// TODO Auto-generated method stub
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		// TODO Auto-generated method stub
		Log.d(TAG, "get preview frame:"+data.length);
		
	}
	
	private void LaunchCamera(){
		if(mCamera == null){
			mCamera.open();
			Camera.Parameters p = mCamera.getParameters();
			//p.getSupportedPreviewFpsRange();
			mCamera.setPreviewCallback(this);
			mCamera.setParameters(p);
			mCamera.startPreview();
		}
	}
	
	class ConnectListener extends Thread{
		private ServerSocket mServerSocket;
		public ConnectListener(){
			try {
				mServerSocket = new ServerSocket(mPort);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		public void run(){
			while(!Thread.interrupted()){
				try {
					new ConnectThread(mServerSocket.accept()).start();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		public void close(){
			try {
				mServerSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//make sure that the thread totally close before the next statement execute
			try {
				this.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	class ConnectThread extends Thread{
		private final Socket mSocket;
		private InputStream mInputStream;
		//private final BufferedReader mInput;
		private final OutputStream mOutputStream;
		private int len;
		private byte[] buf = new byte[1024];
		private String requestCode = null;
		
		
		public ConnectThread(final Socket s) throws IOException{
			mSocket = s;
			mInputStream = mSocket.getInputStream();
			//mInput = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
			mOutputStream = mSocket.getOutputStream();
			
		}
		
		public void run(){
			try {
				while((len = mInputStream.read(buf)) != -1){
					String str = new String(buf, 0, len);
					Pattern p = Pattern.compile("#");
					//00 presents preview
					String[] ss = p.split(str);
					requestCode = ss[0];
					preview_width = Integer.parseInt(ss[1]);
					preview_height = Integer.parseInt(ss[2]);
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		
	}
}
