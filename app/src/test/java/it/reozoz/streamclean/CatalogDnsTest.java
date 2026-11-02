package it.reozoz.streamclean;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogDnsTest {
    private static InetAddress ip(String value)throws Exception{return InetAddress.getByName(value);}
    @Test public void normalDnsDoesNotUseEncryptedFallback()throws Exception{
        InetAddress address=ip("104.21.80.26");
        CatalogDns dns=new CatalogDns(host->List.of(address),host->{throw new AssertionError("Unexpected fallback");});
        assertEquals(List.of(address),dns.lookup("catalog.invalid"));
    }
    @Test public void loopbackDnsIsRetriedWithOriginalHostname()throws Exception{
        InetAddress loopback=ip("127.0.0.1"),address=ip("104.21.80.26");
        CatalogDns dns=new CatalogDns(host->List.of(loopback),host->{assertEquals("streamingcommunityz.build",host);return List.of(address);});
        assertEquals(List.of(address),dns.lookup("streamingcommunityz.build"));
    }
    @Test public void unknownHostIsRetried()throws Exception{
        InetAddress address=ip("104.21.80.26");
        CatalogDns dns=new CatalogDns(host->{throw new UnknownHostException(host);},host->List.of(address));
        assertEquals(List.of(address),dns.lookup("catalog.invalid"));
    }
    @Test public void mixedDnsResultsDiscardOnlyUnusableAddresses()throws Exception{
        InetAddress loopback=ip("::1"),address=ip("104.21.80.26");
        CatalogDns dns=new CatalogDns(host->List.of(loopback,address),host->{throw new AssertionError("Unexpected fallback");});
        assertEquals(List.of(address),dns.lookup("catalog.invalid"));
    }
    @Test public void invalidEncryptedResultsFailWithoutConnectingToLoopback()throws Exception{
        InetAddress loopback=ip("127.0.0.1"),any=ip("0.0.0.0");
        CatalogDns dns=new CatalogDns(host->List.of(loopback),host->List.of(any));
        UnknownHostException failure=assertThrows(UnknownHostException.class,()->dns.lookup("catalog.invalid"));
        assertEquals(1,failure.getSuppressed().length);
    }
}
