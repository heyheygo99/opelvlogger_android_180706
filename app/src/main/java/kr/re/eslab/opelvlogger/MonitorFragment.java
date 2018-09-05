package kr.re.eslab.opelvlogger;

import android.app.ListFragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
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

public class MonitorFragment extends ListFragment {

    ListView monitorItemListView = null;
    static monitorItemAdapter monitorItemListViewAdapter;

    private Button addListItembutton;
    private ToggleButton setID_typeButton;
    private EditText input_ID;

    SharedPreferences sp;
    SharedPreferences.Editor editor;

    private static String folderName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/bOBD/";
    private String fileName = "";

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
            FileWriter fos = new FileWriter(foldername + "/" + filename+".txt", true);
            //파일쓰기
            BufferedWriter writer = new BufferedWriter(fos);
            writer.write(contents+"\r\n");
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

        tt = new TimerTask() {
            int steering, breakV;

            @Override
            public void run() {
                String now = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                String time = new SimpleDateFormat("HH_mm_ss.SSS").format(new Date());

                steering = sp.getInt("Steering", 0);
                breakV = sp.getInt("Break", -1);

                WriteTextFile(folderName, fileName, now+"_"+time+", "+steering/10+", "+ breakV);
            }
        };

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String time = new SimpleDateFormat("HH_mm_ss.SSS").format(new Date());
                fileName = time;

                Timer timer = new Timer();
                timer.schedule(tt, 0, 100);

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

        setListAdapter(monitorItemListViewAdapter) ;
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

                                    if ( mState == UART_PROFILE_CONNECTED) {
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
                if ( mState == UART_PROFILE_CONNECTED ) {
                    String id = null;
                    if (setID_typeButton.getText().equals("Standard OBD-II")) {
                        id = "00";
                        if( input_ID.getText() != null ) {
                            id = input_ID.getText().toString();
                        }
                        if( 2 - id.length() > 0) {
                            for( int i = 2 - id.length(); i > 0; i--) {
                                id = "0" + id;
                            }
                        }

                        tempPacket = "S 000 00 41 " + id.substring(0,2) + " 00 00 00 00 00";
                        // BLE 전송
                        String message = "MONITOR_ADD_PID " + id.substring(0,2);
                        byte[] value = new byte[0];
                        try {
                            value = message.getBytes("UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        mService.writeRXCharacteristic(value);
                    }

                    else if (setID_typeButton.getText().equals("Non-Standard OBD-II")) {
                        id = "000";
                        if( input_ID.getText() != null ) {
                            id = input_ID.getText().toString();
                        }

                        if( 3 - id.length() > 0) {
                            for( int i = 3 - id.length(); i > 0; i--) {
                                id = "0" + id;
                            }
                        }
                        else {
                            id = id.substring(0,3);
                        }
                        tempPacket = "N "+ id + " 00 00 00 00 00 00 00 00";

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
                    if ( addResult == false ) {
                        Toast.makeText(getContext(),"Make less than 20 monitored packets",Toast.LENGTH_SHORT).show();
                    }
                    else {
                        monitorItemListViewAdapter.notifyDataSetChanged();
                    }
                }
                else {
                    Toast.makeText(getActivity(), "Please Connect BLE to Surelogger", Toast.LENGTH_SHORT).show();
                }
            }
        });
        return view;
    }

}
