package kr.re.eslab.opelvlogger;

import android.app.Activity;
import android.app.ListFragment;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import static kr.re.eslab.opelvlogger.MainActivity.UART_PROFILE_CONNECTED;
import static kr.re.eslab.opelvlogger.MainActivity.mService;
import static kr.re.eslab.opelvlogger.MainActivity.mState;

public class MonitorFragment extends ListFragment implements SurfaceHolder.Callback {

    ListView monitorItemListView = null;
    static monitorItemAdapter monitorItemListViewAdapter;

    private Button addListItembutton;
    private ToggleButton setID_typeButton;
    private EditText input_ID;

    SharedPreferences sp;
    SharedPreferences.Editor editor;

    private Camera mCamera = null;
    private MediaRecorder mRecorder = null;

    boolean isRecording = false;

    SurfaceView mSurface = null;
    SurfaceHolder mSurfaceHolder = null;

    private static String folderName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/bOBD/";
    private String fileName = "";

    String timeFile;

    long startTime;
    TimerTask tt;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = this.getActivity().getSharedPreferences("obd", 0);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }


    public void WriteTextFile(String foldername, String filename, String contents) {

        try {
            File dir = new File(foldername);
            //디렉토리 폴더가 없으면 생성함
            if (!dir.exists()) {
                dir.mkdir();
            }
            //파일 output stream 생성
            FileWriter fos = new FileWriter(foldername + "/" + filename + ".txt", true);
            //파일쓰기
            BufferedWriter writer = new BufferedWriter(fos);
            writer.write(contents + "\r\n");
            writer.flush();

            writer.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_monitor_list, container, false);

        addListItembutton = (Button) view.findViewById(R.id.addListItembutton);
        setID_typeButton = (ToggleButton) view.findViewById(R.id.setID_typeButton);
        input_ID = (EditText) view.findViewById(R.id.input_ID);
        monitorItemListView = (ListView) view.findViewById(android.R.id.list);
        // Adapter 생성 및 Adapter 지정.

        Button startBtn = (Button) view.findViewById(R.id.startBtn);
        String now = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        folderName = folderName + now;

        mSurface = (SurfaceView) view.findViewById(R.id.surfaceView);


        tt = new TimerTask() {
            int steering, breakV, Gear, RPM, Speed, Accel;

            @Override
            public void run() {
                String now = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                String time = new SimpleDateFormat("HH_mm_ss.SSS").format(new Date());

                steering = sp.getInt("Steering", 0);
                breakV = sp.getInt("Break", -1);
                Gear = sp.getInt("Gear", -1);
                RPM = sp.getInt("RPM", -1);
                Speed = sp.getInt("Speed", -1);
                Accel = sp.getInt("Accel", -1);

                WriteTextFile(folderName, fileName, now + "_" + time + ", " + steering / 10 + ", " + breakV
                        + ", " + Gear + ", " + RPM + "," + Speed + "," + Accel);
            }
        };

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String time = new SimpleDateFormat("HH_mm_ss.SSS").format(new Date());
                fileName = time;

                Timer timer = new Timer();
                timer.schedule(tt, 0, 250);

                monitorItemListView.setVisibility(View.GONE);
                mSurface.setVisibility(View.VISIBLE);

                initVideoRecorder();
                startVideoRecorder();
            }
        });


        String message = "MONITOR";
        byte[] value = new byte[0];
        try {
            value = message.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        mService.writeRXCharacteristic(value);

        setListAdapter(monitorItemListViewAdapter);
        monitorItemListViewAdapter.notifyDataSetChanged();

        SwipeDismissListViewTouchListener touchListener =
                new SwipeDismissListViewTouchListener(monitorItemListView,
                        new SwipeDismissListViewTouchListener.DismissCallbacks() {
                            @Override
                            public boolean canDismiss(int position) {
                                return true;
                            }

                            @Override
                            public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                for (int position : reverseSortedPositions) {

                                    if (mState == UART_PROFILE_CONNECTED) {
                                        String removeMessage;

                                        if (monitorItemListViewAdapter.getItem(position).get_Std_flag().equals("S")) {
                                            removeMessage = "MONITOR_REMOVE_PID " + monitorItemListViewAdapter.getItem(position).get_data(2);

                                        } else {
                                            removeMessage = "MONITOR_REMOVE_ID " + monitorItemListViewAdapter.getItem(position).get_MsgID();
                                        }
                                        byte[] value = new byte[0];
                                        try {
                                            value = removeMessage.getBytes("UTF-8");
                                        } catch (UnsupportedEncodingException e) {
                                            e.printStackTrace();
                                        }
                                        mService.writeRXCharacteristic(value);

                                        monitorItemListViewAdapter.removeItem(position);
                                    }
                                }
                                monitorItemListViewAdapter.notifyDataSetChanged();
                            }
                        });
        monitorItemListView.setOnTouchListener(touchListener);
        monitorItemListView.setOnScrollListener(touchListener.makeScrollListener());


        addListItembutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tempPacket = null;
                if (mState == UART_PROFILE_CONNECTED) {
                    String id = null;
                    if (setID_typeButton.getText().equals("Standard OBD-II")) {
                        id = "00";
                        if (input_ID.getText() != null) {
                            id = input_ID.getText().toString();
                        }
                        if (2 - id.length() > 0) {
                            for (int i = 2 - id.length(); i > 0; i--) {
                                id = "0" + id;
                            }
                        }

                        tempPacket = "S 000 00 41 " + id.substring(0, 2) + " 00 00 00 00 00";
                        // BLE 전송
                        String message = "MONITOR_ADD_PID " + id.substring(0, 2);
                        byte[] value = new byte[0];
                        try {
                            value = message.getBytes("UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        mService.writeRXCharacteristic(value);
                    } else if (setID_typeButton.getText().equals("Non-Standard OBD-II")) {
                        id = "000";
                        if (input_ID.getText() != null) {
                            id = input_ID.getText().toString();
                        }

                        if (3 - id.length() > 0) {
                            for (int i = 3 - id.length(); i > 0; i--) {
                                id = "0" + id;
                            }
                        } else {
                            id = id.substring(0, 3);
                        }
                        tempPacket = "N " + id + " 00 00 00 00 00 00 00 00";

                        String message = "MONITOR_ADD_ID " + id;
                        byte[] value = new byte[0];
                        try {
                            value = message.getBytes("UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        mService.writeRXCharacteristic(value);
                    }

                    boolean addResult = monitorItemListViewAdapter.addItem(tempPacket);
                    if (addResult == false) {
                        Toast.makeText(getContext(), "Make less than 20 monitored packets", Toast.LENGTH_SHORT).show();
                    } else {
                        monitorItemListViewAdapter.notifyDataSetChanged();
                    }
                } else {
                    Toast.makeText(getActivity(), "Please Connect BLE to Surelogger", Toast.LENGTH_SHORT).show();
                }
            }
        });
        return view;
    }

    public void initVideoRecorder() {
        mCamera = Camera.open();
        Camera.Parameters p = mCamera.getParameters();
        p.setPreviewFpsRange(30000, 30000);
//        if(p.isAutoExposureLockSupported())
//            p.setAutoExposureLock(true);
        mCamera.setParameters(p);
//        mCamera.cancelAutoFocus();
//        mCamera.unlock();
//        mCamera.setDisplayOrientation(90);
        mSurfaceHolder = mSurface.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

    }

    public void startVideoRecorder() {

        if (isRecording) {

            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;

            mCamera.lock();
            isRecording = false;
        } else {
            isRecording = true;

            this.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String now = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                    timeFile = new SimpleDateFormat("HH_mm_ss").format(new Date());
                    String mFolderName = folderName + now;
                    startTime = System.currentTimeMillis();
                    File file = new File(mFolderName);
                    if (!file.exists())
                        file.mkdir();
                    try {

                        mRecorder = new MediaRecorder();
                        mCamera.unlock();
                        mRecorder.setCamera(mCamera);
                        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                        mRecorder.setOutputFile(mFolderName + "/" + timeFile + ".mp4");
                        mRecorder.setVideoFrameRate(30);
                        mRecorder.setVideoEncodingBitRate(17300000);
                        mRecorder.setVideoSize(1280, 720);
                        mRecorder.setOrientationHint(0);
                        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                        mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

                        mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
                        mRecorder.setMaxFileSize(0);
                        mRecorder.prepare();
                        mRecorder.start();

                        isRecording = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            // 메타 데이터 입력
            // 운전 영상 녹화 시, 시작 시간 및 종료 시간 (전체 영상 시간)
            //
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        if (mCamera == null) {
            try {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                Log.e("test0611", "in, surface");
                mCamera.startPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mCamera.stopPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        if (isRecording) {
            mRecorder.stop();
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
        }

        folderName = "";
        fileName = "";
    }
}
