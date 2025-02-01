import java.util.*;

public class Peer {
    private String address;
    private int port;
    private List<String> files;
    
    public Peer(String address, int port) {
        this.address = address;
        this.port = port;
        this.files = new ArrayList<>();
    }
    
    public String getAddress() {
        return address;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setFiles(List<String> files) {
        this.files = new ArrayList<>(files);
    }
    
    public List<String> getFiles() {
        return new ArrayList<>(files);
    }
    
    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Peer peer = (Peer) object;
        return port == peer.port && Objects.equals(address, peer.address);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(address, port);
    }
}