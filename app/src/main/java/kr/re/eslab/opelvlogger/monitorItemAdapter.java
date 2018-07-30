package kr.re.eslab.opelvlogger;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class monitorItemAdapter extends BaseAdapter {

    public static ArrayList<MonitorItem> obd_list = new ArrayList<MonitorItem>();

    @Override
    public int getCount() {
        return obd_list.size();
    }

    @Override
    public MonitorItem getItem(int position) {
        return obd_list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View view, ViewGroup viewGroup) {
        final int pos = position;
        final Context context = viewGroup.getContext();

        // "listview_item" Layout을 inflate하여 convertView 참조 획득.
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.fragment_monitor_item, viewGroup, false);
        }

        // 화면에 표시될 View(Layout이 inflate된)으로부터 위젯에 대한 참조 획득
        TextView CAN_PACKET = (TextView) view.findViewById(R.id.can_packet);

        // Data Set(listViewItemList)에서 position에 위치한 데이터 참조 획득
        MonitorItem obd_list_view_item = obd_list.get(position);

        // 아이템 내 각 위젯에 데이터 반영
        CAN_PACKET.setText(obd_list_view_item.CAN_PACKET);

        return view;
    }
    public boolean addItem(String can_packet) {
        int MONITOR_MAX = 20;
        if( obd_list.size() < MONITOR_MAX ) {
            MonitorItem item = new MonitorItem();
            item.set_item(can_packet);
            obd_list.add(item);
            return true;
        }
        return false;
    }

    public void setItem(int position, String CAN_packet){
        obd_list.get(position).set_item(CAN_packet);
    }

    public void removeItem(int position) {
        obd_list.remove(position);
    }


}