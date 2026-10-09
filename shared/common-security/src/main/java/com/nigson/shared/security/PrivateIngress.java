package com.nigson.shared.security;

import java.net.InetAddress;
import java.util.*;
/** Peer socket CIDRs only. Never authorizes based on forwarded headers or DNS. */
public final class PrivateIngress {
    private record Network(byte[] address,int bits) {}
    private final List<Network> networks;
    public PrivateIngress(Collection<String> cidrs) {
        if(cidrs.isEmpty()||cidrs.size()>32)throw new IllegalArgumentException("PRIVATE_CIDRS_REQUIRED");
        networks=cidrs.stream().map(c->{try {String[] p=c.split("/",-1);byte[] a=literal(p[0]);int bits=Integer.parseInt(p[1]);if(p.length!=2||bits<1||bits>a.length*8)throw new IllegalArgumentException();return new Network(a,bits);}catch(Exception e){throw new IllegalArgumentException("PRIVATE_CIDR_INVALID");}}).toList();
    }
    public boolean permits(String socketAddress) {
        try{byte[] a=literal(socketAddress);for(var n:networks){if(a.length!=n.address.length)continue;boolean matches=true;for(int i=0;i<n.bits;i++)if((a[i/8]&(1<<(7-i%8)))!=(n.address[i/8]&(1<<(7-i%8)))){matches=false;break;}if(matches)return true;}return false;}catch(Exception e){return false;}
    }
    private static byte[] literal(String value)throws Exception {
        if(value==null||value.length()>45||!value.matches("[0-9A-Fa-f:.]+"))throw new IllegalArgumentException();
        if(!value.contains(":")){String[] p=value.split("\\.",-1);if(p.length!=4)throw new IllegalArgumentException();byte[] a=new byte[4];for(int i=0;i<4;i++){if(!p[i].matches("0|[1-9][0-9]{0,2}")||Integer.parseInt(p[i])>255)throw new IllegalArgumentException();a[i]=(byte)Integer.parseInt(p[i]);}return a;}
        return InetAddress.getByName(value).getAddress();
    }
}
