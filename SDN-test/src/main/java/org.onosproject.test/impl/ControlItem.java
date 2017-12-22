package org.onosproject.test.impl;

/**
 * Created by jiayit on 7/10/17.
 */
public class ControlItem {

        private String srcIp;
        private String dstIp;
        private String protocol;
        private int port;

        public ControlItem(String srcIp, String dstIp, String protocol, int port) {
            this.srcIp = srcIp;
            this.dstIp = dstIp;
            this.protocol = protocol;
            this.port = port;
        }

        public String getSrcIp() {
            return srcIp;
        }
        public String getDstIp() {
            return dstIp;
        }
        public String getProtocol() {
            return  protocol;
        }
        public int getPort() {
            return  port;
        }

        public int hashCode() {
            return (srcIp.hashCode() + dstIp.hashCode() + protocol.hashCode() + port);
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof ControlItem)) {
               return false;
            }
            ControlItem item = (ControlItem) obj;
            return (item.srcIp.equals(this.srcIp)) && (item.dstIp.equals(this.dstIp))
                    && (item.protocol.equals(this.protocol)) && (item.port == this.port);

        }



}
