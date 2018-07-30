package kr.re.eslab.opelvlogger;

/**
 * Created by DH_Han on 2018-05-22.
 */

public class MonitorItem {
    public String CAN_PACKET;
    private String[] CAN_PACKET_SPLIT;

    public MonitorItem( ) {
        this.CAN_PACKET = null;
        CAN_PACKET_SPLIT = new String[10];
    }

    public String get_MsgID() {
        return CAN_PACKET_SPLIT[1];
    }

    public String get_data(int index) {
        if( index >= 0 && index < 8) {
            return CAN_PACKET_SPLIT[index + 2];
        }
        return null;
    }

    public String get_Std_flag() {
        return CAN_PACKET_SPLIT[0];
    }


    public void set_item(String CAN_packet) {
        if (CAN_packet != null) {
            CAN_PACKET = CAN_packet;
            CAN_PACKET_SPLIT = CAN_PACKET.split(" ");
        }
    }
}
