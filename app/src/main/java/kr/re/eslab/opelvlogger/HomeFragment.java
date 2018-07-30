package kr.re.eslab.opelvlogger;


import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.io.UnsupportedEncodingException;

import static kr.re.eslab.opelvlogger.MainActivity.UART_PROFILE_CONNECTED;
import static kr.re.eslab.opelvlogger.MainActivity.mService;
import static kr.re.eslab.opelvlogger.MainActivity.mState;


public class HomeFragment extends Fragment {

    static public Button mBtnGallery, mBtnSetting, mBtnLogin, mBtnInfo;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        if( mState == UART_PROFILE_CONNECTED ) {
            String message = "IDLE";
            byte[] value = new byte[0];
            try {
                value = message.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            mService.writeRXCharacteristic(value);
        }


        return view;
    }

}
