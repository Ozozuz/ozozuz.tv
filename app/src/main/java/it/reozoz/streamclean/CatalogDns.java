package it.reozoz.streamclean;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Dns;

/** Retry catalogue DNS over HTTPS only if the system lookup fails or returns unusable addresses. */
final class CatalogDns implements Dns {
    private final Dns system,secure;
    CatalogDns(Dns system,Dns secure){this.system=system;this.secure=secure;}
    @Override public List<InetAddress> lookup(String hostname)throws UnknownHostException{
        UnknownHostException original;
        try{
            List<InetAddress> addresses=usable(system.lookup(hostname));
            if(!addresses.isEmpty())return addresses;
            original=new UnknownHostException("DNS locale non utilizzabile per "+hostname);
        }catch(UnknownHostException failure){original=failure;}
        if(Thread.currentThread().isInterrupted())throw original;
        try{
            List<InetAddress> addresses=usable(secure.lookup(hostname));
            if(!addresses.isEmpty())return addresses;
            throw new UnknownHostException("DNS HTTPS senza indirizzi utilizzabili per "+hostname);
        }catch(UnknownHostException failure){failure.addSuppressed(original);throw failure;}
    }
    private static List<InetAddress> usable(List<InetAddress> addresses){
        List<InetAddress> result=new ArrayList<>();
        for(InetAddress address:addresses)if(!address.isLoopbackAddress()&&!address.isAnyLocalAddress()&&!address.isLinkLocalAddress())result.add(address);
        return result;
    }
}
