/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.daizhx.cameraviawifidirect;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.googlecode.javacv.FFmpegFrameRecorder;
import com.googlecode.javacv.FrameRecorder.Exception;
import static com.googlecode.javacv.cpp.opencv_core.*;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
public class WiFiDirectClientActivity extends Activity implements Callback,Camera.PreviewCallback, 
ChannelListener,ConnectionInfoListener,PeerListListener{

    public static final String TAG = "ipcamera";
    private static final boolean DEBUG = true;
    private WifiP2pManager manager;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;
    
    private boolean isConnected = false;

    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;
    
    private SurfaceView mSurfaceView = null;
    private SurfaceHolder mSurfaceHolder = null;
    private Camera mCamera = null;
    private Camera.Parameters mCameraParam;
    public final static int SEND_JPG_FROM_PREVIEW = 0;
    public final static int SEND_JPG_FROM_CAMERA = 1;

    private WifiP2pDevice device;
    private WifiP2pInfo info;
    private static final int SOCKET_TIMEOUT = 5000;
    
    //private TextView local_device;
    //private TextView remote_device;
    private String make_dir = "IPCamera";
    
	private static int switcher = 0;
	private int preview_width;
	private int preview_height;
	
	private FFmpegFrameRecorder recorder;
	private IplImage yuvIplimage = null;
	
	private long startTime = 0;
	private boolean recording = false;
	private String recordPath = null;
	
	private PreviewThread previewThread = null;
	
	private boolean previewEnable = false;
	private Handler mHandler;
  
    private AudioRecord audioRecord;
    private int sampleAudioRateInHz = 44100;
    volatile boolean runAudioThread = true;
    
    private AudioRecordRunnable audioRecordRunnable;
    private Thread audioThread;
    
    private int takePictureMode;
    private static final int SAVE_LOCAL = 0;
    private static final int SAVE_APP = 1;
    private static final int SAVE_BOTH = 2;
    
    private String password = null;
    public static final String PREFS_NAME = "somedata";
    public static final String KEY_PW = "PW";
    
    private static final int MSG_CLOSE_PREVIEW = 1;

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // add necessary intent values to be matched.

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        
        //      
        mSurfaceView = (SurfaceView) this.findViewById(R.id.surface_camera);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        //local_device = (TextView)findViewById(R.id.local_device);
        //remote_device = (TextView)findViewById(R.id.remote_device);
        
        if(manager != null){
        	manager.createGroup(channel, new ActionListener(){
				@Override
				public void onFailure(int arg0) {
					// TODO Auto-generated method stub
					Toast.makeText(WiFiDirectClientActivity.this, "initiate create group fail, try again later!", Toast.LENGTH_SHORT).show();
				}

				@Override
				public void onSuccess() {
					// TODO Auto-generated method stub
					Toast.makeText(WiFiDirectClientActivity.this, "initiate create group success!", Toast.LENGTH_SHORT).show();
				}
        		
        	});
        }
        new FileUtil().creatSDDir(make_dir);
        mHandler = new Handler(){

			@Override
			public void handleMessage(Message msg) {
				Log.d(TAG, "handleMessage:msg.what="+msg.what);
				// TODO Auto-generated method stub
				switch(msg.what){
				//connect failed,restart thread
				case 0:
					previewThread = null;
					startPreviewThread();
					break;
				case MSG_CLOSE_PREVIEW:
					stopPreview();
					break;
				}
			}
        	
        };
        
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        password=sp.getString(KEY_PW, "000000");
        
    }

    /** register the BroadcastReceiver with the intent values to be matched */
    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
        
        startPreviewThread();
        //startListenThread();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        
        //close socket while the phone will read 0 byte in circure
        //previewThread.close();
    }

    @Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
    	if(previewThread != null){
    		previewThread.close();
    		previewThread = null;
    	}
		super.onDestroy();
	}

    
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		if(keyCode == KeyEvent.KEYCODE_VOLUME_UP){
			savePicture();
			takePictureMode = SAVE_LOCAL;
		}
		//return super.onKeyDown(keyCode, event);
		return true;
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.atn_direct_enable:
                return true;

            case R.id.atn_direct_discover:
                if (!isWifiP2pEnabled) {
                    Toast.makeText(WiFiDirectClientActivity.this, R.string.p2p_off_warning,
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectClientActivity.this, "Discovery Initiated",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectClientActivity.this, "Discovery Failed : " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    //---------------------------------------------
    // audio thread, gets and encodes audio data
    //---------------------------------------------
    class AudioRecordRunnable implements Runnable {

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Audio
            int bufferSize;
            short[] audioData;
            int bufferReadResult;

            bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz, 
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz, 
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioData = new short[bufferSize];

            Log.d(TAG, "audioRecord.startRecording()");
            audioRecord.startRecording();

            /* ffmpeg_audio encoding loop */
            while (runAudioThread) {
                //Log.v(LOG_TAG,"recording? " + recording);
                bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
                if (bufferReadResult > 0) {
                    Log.v(TAG,"bufferReadResult: " + bufferReadResult);
                    // If "recording" isn't true when start this thread, it never get's set according to this if statement...!!!
                    // Why?  Good question...
                    if (recording) {
                        try {
                            //recorder.record(ShortBuffer.wrap(audioData, 0, bufferReadResult));
                        	Buffer[] buffers = new Buffer[1];
                            buffers[0]= ShortBuffer.wrap(audioData, 0, bufferReadResult);
                            recorder.record(buffers);
                            //Log.v(LOG_TAG,"recording " + 1024*i + " to " + 1024*i+1024);
                        } catch (FFmpegFrameRecorder.Exception e) {
                            Log.v(TAG,e.getMessage());
                            e.printStackTrace();
                        }
                    }else{
                    	break;//over the loop
                    }
                }
            }
            Log.v(TAG,"AudioThread Finished, release audioRecord");

            /* encoding finish, release recorder */
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.v(TAG,"audioRecord released");
            }
        }
    }

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		Log.d(TAG, "onPreviewFrame-data size:"+data.length);
		Log.d(TAG, "onPreviewFrame-start:"+System.currentTimeMillis());
	    //connect or disconnect
		if(previewEnable){
			Size size = camera.getParameters().getPreviewSize();
			YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null); 
			if(image!=null){
				ByteArrayOutputStream outstream = new ByteArrayOutputStream();
			    image.compressToJpeg(new Rect(0, 0, size.width, size.height), 50, outstream);
			    wirteDateToSocket(outstream);
				//outstream.flush();
			    Log.d(TAG, "onPreviewFrame-end:"+System.currentTimeMillis());
				RecordingVideo(data);
			}
		}else{
			//start a timer then close camera preview for saving power
			Timer timer = new Timer();
			timer.schedule(new TimerTask(){

				@Override
				public void run() {
					// TODO Auto-generated method stub
					Message msg = Message.obtain();
	                msg.what = MSG_CLOSE_PREVIEW;
	                mHandler.sendMessage(msg);
				}				
			}, 60*1000);
		}

	}
	
	private void startPreview(){

		Camera.Parameters p = mCamera.getParameters();
		List<Size> sizes= p.getSupportedPreviewSizes();
		Size optimalSize = null;
		if(preview_width != 0 && preview_height != 0){
			optimalSize = getOptimalPreviewSize(sizes, (double)preview_width/preview_height);
		}
		if(DEBUG){
			for(Size size: sizes){
				Log.d(TAG, "Supported preview size:"+size.width+"x"+size.height);
			}
			Log.d(TAG, "optimalSize="+optimalSize.width+"x"+optimalSize.height);
		}
		p.setPreviewFpsRange(15,20);
		//p.setPreviewFrameRate(15);
		p.setPictureFormat(ImageFormat.NV21);
		p.setPreviewSize(optimalSize.width,optimalSize.height);
		//mCamera.setDisplayOrientation(90);
		mCamera.setPreviewCallback(this);
		mCamera.setParameters(p);
		
		mCamera.startPreview();
	}
	
	private void stopPreview(){
		mCamera.stopPreview();
	}
	
	private void openCamera() {
		if (mCamera == null) {
			mCamera = Camera.open();
	    }
		//mCameraParam = mCamera.getParameters();
		
	}

	private Camera.Size getOptimalPreviewSize(List<Size> sizes, double targetRatio)
	{
	      final double ASPECT_TOLERANCE = 0.05;
	      if (sizes == null) return null;

	      Size optimalSize = null;
	      double minDiff = Double.MAX_VALUE;

	      int targetHeight = Math.min(preview_height, preview_width);

	      // try to find a size larger but closet to the desired preview size
	      for (Size size : sizes) {
	          if (targetHeight > size.height) continue;
	          
	          double ratio = (double) size.width / size.height;
	          if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
	          if (Math.abs(size.height - targetHeight) < minDiff) {
	              optimalSize = size;
	              minDiff = Math.abs(size.height - targetHeight);
	          }
	      }

	      // not found, apply origional policy.
	      if (optimalSize == null) {

	          // Try to find an size match aspect ratio and size
	          for (Size size : sizes) {
	              double ratio = (double) size.width / size.height;
	              if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
	              if (Math.abs(size.height - targetHeight) < minDiff) {
	                  optimalSize = size;
	                  minDiff = Math.abs(size.height - targetHeight);
	              }
	          }
	      }

	      // Cannot find the one match the aspect ratio, ignore the requirement
	      if (optimalSize == null) {
	          //Log.v(TAG, "No preview size match the aspect ratio");
	          minDiff = Double.MAX_VALUE;
	          for (Size size : sizes) {
	              if (Math.abs(size.height - targetHeight) < minDiff) {
	                  optimalSize = size;
	                  minDiff = Math.abs(size.height - targetHeight);
	              }
	          }
	      }
	      return optimalSize;
	  }
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		
		try {
			mCamera.setPreviewDisplay(holder);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		openCamera();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (mCamera != null) {
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
		
	}
	private long mJpegPictureTime;
	private PictureCallback pictureCallback = new PictureCallback(){

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			// TODO Auto-generated method stub
			mJpegPictureTime = System.currentTimeMillis();
			FileOutputStream fout;
			{
			if(takePictureMode == SAVE_LOCAL || takePictureMode == SAVE_BOTH){	
				String rootpath = new FileUtil().getSDCardRoot() + make_dir + File.separator;
				try {
					fout = new FileOutputStream(rootpath + "Test_JPG_"+mJpegPictureTime+".jpg");
					fout.write(data);
					fout.close();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}catch(IOException e){
					
				}
			}
				
			if(takePictureMode == SAVE_APP || takePictureMode == SAVE_BOTH){	
				//send the pic
				Log.d(TAG, "data len="+data.length);
				Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
				ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();
				bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baOutputStream);
				long picSize = baOutputStream.size();
				Log.d(TAG, "compress jpeg len="+picSize);
				
				byte[] head = new byte[9];
				head[0] = 0x02;
				byte[] lenbytes = longToBytes(picSize);
				for(int i=0; i<8; i++){
					head[i+1] = lenbytes[i];
				}
				if(previewThread != null){
					previewThread.write(head);
					previewThread.write(baOutputStream.toByteArray());
				}
				//previewThread.write(output)
			}	
			}
        	
			
			mCamera.startPreview();
			
		}
		
	};

	public void savePicture() {
		if (mCamera != null) { 
			mCamera.autoFocus(new AutoFocusCallback() { 
				@Override 
				public void onAutoFocus(boolean success, Camera camera) { 
						//if (success) {
							Log.d(TAG, "camera take picture");
							camera.takePicture(null, null, pictureCallback); 
						//} 
				} 
			}); 
		} 
		
	}

	public void startRecording() {
		Log.d(TAG, "start Recording");
		recordPath = new FileUtil().getSDCardRoot()+make_dir + File.separator + "Video_" + System.currentTimeMillis()+".mp4";
		
		recorder = new FFmpegFrameRecorder(
				recordPath,
				preview_width,
				preview_height, 1);
		
		recorder.setFormat("mp4");
		recorder.setFrameRate(15f);//
		
		audioRecordRunnable = new AudioRecordRunnable();
        audioThread = new Thread(audioRecordRunnable);
		
		if (yuvIplimage == null) {
            yuvIplimage = IplImage.create(preview_width, preview_height, IPL_DEPTH_8U, 2);
            Log.i(TAG, "create yuvIplimage");
        }
        try {
            recorder.start();
            startTime = System.currentTimeMillis();
            recording = true;
            audioThread.start();

        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
        
    }
	
	public void RecordingVideo(byte[] data){
		if (yuvIplimage != null && recording) {
			yuvIplimage.getByteBuffer().put(data);
			try {
                long t = 1000 * (System.currentTimeMillis() - startTime);
                if (t > recorder.getTimestamp()) {
                    recorder.setTimestamp(t);
                }
                recorder.record(yuvIplimage);
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
		}
	}

    public void stopRecording() {
    	Log.d(TAG, "stop Recording");
        if (recorder != null && recording) {
            recording = false;
            Log.v(TAG,"Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }
        disablePreview();
        sendVideo();
        enablePreview();
    }

    private void disablePreview(){
    	previewEnable = false;
    }
    
    private void enablePreview(){
    	previewEnable = true;
    }

    public byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(x);
        return buffer.array();
    }

    public long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(bytes);
        buffer.flip();//need flip 
        return buffer.getLong();
    }
    
    private void sendVideo(){
    	InputStream in = null;
    	byte[] buf = new byte[4048];
    	File file = new File(recordPath);
    	long fileSize = file.length();
    	Log.d(TAG, "Video file size="+fileSize);

        try {
			in = new BufferedInputStream(new FileInputStream(file));
			
			byte[] head = new byte[9];
			head[0] = 0x03;
			byte[] bytes = longToBytes(fileSize);
			for(int i=1; i<9; i++){
				head[i] = bytes[i-1];
			}
			if(previewThread != null){
				previewThread.write(head);
				int len = 0;
				while((len = in.read(buf)) != -1){
					previewThread.writebuf(buf, 0, len);
				}
			}	
			
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, Log.getStackTraceString(e));
		}catch (IOException e){
			Log.d(TAG, Log.getStackTraceString(e));
		}
        //write end and open preview
        
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
        isConnected = false;
    }
    
    
	@Override
	public void onPeersAvailable(WifiP2pDeviceList peers) {
		// TODO Auto-generated method stub
		Log.d(TAG, "Available Peers size = " + peers.getDeviceList().size());
		for(WifiP2pDevice peer : peers.getDeviceList()){
			Log.d(TAG, "peersList:"+peer.deviceName+","+peer.deviceAddress);
		}
	}

	@Override
	public void onConnectionInfoAvailable(WifiP2pInfo info) {
		// TODO Auto-generated method stub
		isConnected = true;
		this.info = info;
		
        if (this.info.groupFormed && this.info.isGroupOwner) {
           //TODO
        } else if (this.info.groupFormed) {
        	//TODO
        }
        //startConnectedThread();        
	}

	
	public void startPreviewThread(){
		if(previewThread == null){
			previewThread = new PreviewThread();
			previewThread.start();			
		}	
	}
	

	
    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            //out.close();
            //inputStream.close();
        } catch (IOException e) {
            Log.d(WiFiDirectClientActivity.TAG, e.toString());
            return false;
        }
        return true;
    }
	
	public void wirteDateToSocket(ByteArrayOutputStream outStream){
		long size = outStream.size();
		byte[] lenbytes = longToBytes(size);
		byte[] head= new byte[9];
		head[0] = 0x01;
		for(int i=0; i<8; i++){
			head[i+1] = lenbytes[i];
		}
		//Log.d(TAG, "send data, data size="+size);
		if(previewThread != null){
			previewThread.write(head);
			previewThread.write(outStream.toByteArray());
		}
				
    }
	
	//utils
	private byte[] int2bytes(int num){
		byte[] buf = new byte[4];
		for(int i = 0; i < 4; i++){
			buf[i] = (byte)(num >>> (24 - i*8));
		}
		return buf;
	}
	
	private void int2bytes(int num, byte[] buf, int offset){
		for(int i = offset; i < offset+4; i++){
			buf[i] = (byte)(num >>> (24 - i*8));
		}
	}
	
	
	//work for preview
	private class PreviewThread extends Thread{
		private ServerSocket serverSocket;
		private Socket socket;
		private OutputStream outputStream;
		private InputStream inputStream;
		private byte[] buf = new byte[1024];
		private int len;
		private int socketState;
		private static final int SOCKET_CLOSE = 0;
		private static final int SOCKET_LISTEN = 1;
		private static final int SOCKET_CONNECT = 2;
		
		
		public PreviewThread(){
    		try {
				serverSocket = new ServerSocket(8988);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.e(TAG, "new ServerSocet fail,e="+e);
				//should restart
			}
    		if(DEBUG){
    			Log.d(TAG, "created serverSocket sucessfully");
    		}
    		socketState = SOCKET_LISTEN;
    	}
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			if(serverSocket != null){
				try {
				Log.d(TAG, "socket Listen...");
				socket = serverSocket.accept();
				outputStream = socket.getOutputStream();
				inputStream = socket.getInputStream();
				previewEnable = true;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.d(TAG, "socket listen exception:"+e);
					//should restart
				}
				Log.d(TAG, "socket connected");
				socketState = SOCKET_CONNECT;
			}
			
			try {
				//Blocking read?yes
				while((len = inputStream.read(buf)) != -1){
						String readStr = new String(buf, 0, len);
						Log.d(TAG, "read command="+readStr);
						String[] readStrArray = readStr.split("#", 2);
						String cmd = null;
						for(int i=0; i<readStrArray.length; i++){
							cmd = readStrArray[0];
						}
						if(cmd.equals("preview")){
							String[] params = (readStrArray[1]).split("#");
							String password = params[0];
							if(!checkPW(password)){
							//password is wrong,close connect
								byte[] bytes = {0x04};
								write(bytes);
								closeAndRestart();
								break;
							}
							preview_width = Integer.parseInt(params[1]);
							preview_height =  Integer.parseInt(params[2]);
							startPreview();
							
						}else if(cmd.equals("exit")){
							closeAndRestart();
							return;
			            }else if(cmd.equals("capture")){
			            	takePictureMode = SAVE_APP;
			            	savePicture();
			            }else if(cmd.equals("video")){
			            	startRecording();
			            }else if(cmd.equals("stopVideo")){
			            	stopRecording();
			            }
			            else{
			            	//TODO
			            }
				}
			} catch (IOException e) {
					// TODO Auto-generated catch block
				Log.d(TAG, "socket read exception:"+e);
				connectFail();
				return;
			}
		}
		
		
		private boolean checkPW(String pw){
			if(pw.equals(password))return true;
			return false;
		}
		private void closeAndRestart(){
			previewEnable = false;
			close();
			previewThread = null;
			Message msg = Message.obtain();
			msg.what = 0;
			mHandler.sendMessage(msg);
		}
		public void connectFail(){
			if(socketState != SOCKET_CONNECT)return;//avoid running one more time
			socketState = SOCKET_CLOSE;
			previewEnable = false;
			close();
			Message msg = Message.obtain();
			msg.what = 0;
			mHandler.sendMessage(msg);
		}
		
		public void write(byte[] output){
			try {
				outputStream.write(output);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.d(TAG, "socket write exception:"+e);
				connectFail();
			}
		}
		
		public void writebuf(byte[] buf, int offset, int count){
			try {
				outputStream.write(buf, offset, count);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.d(TAG, "socket write exception:"+e);
				connectFail();
			}			
	}
	public void close(){
		
			try {
				if(socket != null){
					socket.close();
					serverSocket.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.e(TAG, Log.getStackTraceString(e));
			}
			
		}
	}
	
}
