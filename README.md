# P2P File Sharing Application

This is a university project for CSE471: Data Communications and Computer Networks at Yeditepe University. A decentralized peer-to-peer file sharing application built in Java that enables users to share and download files across a network without relying on a central server. 

## Features

### UDP Network Discovery
- Automatic peer discovery using UDP flooding with scope limiting 
### File Transfer
-  Chunked file transfer (256KB chunks)
- Support for downloading different pieces from multiple peers 
- File integrity verification using SHA-256 checksums
- Progress tracking for active downloads
### User Interface
- Graphical interface for easy file management
- Configurable shared and destination folders
- Real-time download progress monitoring
- File and folder exclusion capabilities
### Network Management:
- Automatic peer discovery and maintenance
- Connection status monitoring
- Heartbeat mechanism for peer availability tracking


## Usage

### Initial Setup

1. Launch the application
2. Set the root shared folder using the "Set" button next to "Root of the P2P shared folder"
3. Set the destination folder for downloads using the "Set" button next to "Destination folder"
4. Click "Connect" in the Files menu to join the P2P network

### Basic Operations

- **Sharing Files**: Place files in the configured shared folder
- **Downloading Files**: Double-click on files in the "Found files" list to start downloading
- **Excluding Content**:
    - Add folders to exclude using the "Add" button in the folder exclusion section
    - Add file masks to exclude specific file types or names
    - Use "Del" buttons to remove exclusions

### Menu Options

- **Files**
    - Connect: Join the P2P network
    - Disconnect: Leave the P2P network
    - Exit: Close the application
- **Help**
    - About: View developer information

## Class Structure

### P2PConnection.java 
- Handles peer discovery and network communication
### P2PHandling.java
- Manages file transfers and data handling
### Peer.java
- Represents peer information and capabilities
### MainMenu.java
- Implements the graphical user interface

### Network Protocol
- Uses UDP for peer discovery (port 8888)
- TCP for file transfers
- Implements heartbeat mechanism for peer status monitoring

### File Transfer

- Files are transferred in 256KB chunks
- SHA-256 checksums verify file integrity
- Support for parallel downloads from multiple peers
- Automatic reassembly of downloaded chunks

## Bonus Features

- Folder exclusion within shared directory
- File mask-based exclusion
- Support for multiple low memory footprint deployments

## Limitations

- Limited to local network by default
- Requires proper network configuration for cross-network operation
- UDP broadcast might be blocked by some network configurations




