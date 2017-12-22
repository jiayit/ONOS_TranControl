package org.onosproject.test.intf;

import org.onosproject.test.impl.ControlItem;

import java.util.Set;

/**
 * Created by jiayit on 6/28/17.
 */
public interface TransportConService  {

     boolean addBlocklist(String srcip, String dstip, String protocol, int port);
     boolean deleteBlocklist(String srcip, String dstip, String protocol, int port);
     Set<ControlItem> getControlItem();
}
