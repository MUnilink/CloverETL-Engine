/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2002-04  David Pavlis <david_pavlis@hotmail.com>
*    
*    This library is free software; you can redistribute it and/or
*    modify it under the terms of the GNU Lesser General Public
*    License as published by the Free Software Foundation; either
*    version 2.1 of the License, or (at your option) any later version.
*    
*    This library is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
*    Lesser General Public License for more details.
*    
*    You should have received a copy of the GNU Lesser General Public
*    License along with this library; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*/
package org.jetel.data;

import static org.jetel.util.bytes.ByteBufferUtils.decodeLength;
import static org.jetel.util.bytes.ByteBufferUtils.encodeLength;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  Class implementing DynamicRecordBuffer backed by temporary file - i.e. unlimited
 *  size<br>
 *  Implements FIFO: write & read operations can be interleaved, however it
 *  deteriorates performance if data has to be swap to disk (if internal
 *  buffer is exhausted).<br>
 *  
 *
 *@author     David Pavlis
 *@since      November 20, 2006
 */
public class DynamicRecordBuffer {

	private FileChannel tmpFileChannel;
	private File tmpFile;
	private String tmpFilePath;

	private ByteBuffer readDataBuffer;
    private ByteBuffer writeDataBuffer;
    private ByteBuffer tmpDataRecord;

	private LinkedList<DiskSlot> emptyFileBuffers;
    private LinkedList<DiskSlot> fullFileBuffers;
    
    private AtomicInteger bufferedRecords;
    
    private boolean awaitingData;
    
    private int lastSlot;
    private int dataBufferSize;
    
	private boolean hasFile;   // indicates whether buffer contains unwritten data
	private boolean isClosed;  // indicates whether buffer has been closed - no more r&w can occure


	// size of data (in bytes) needed to store record length
	private final static int LEN_SIZE_SPECIFIER = 4;
	// size of integer variable used to keep record length
    
	private final static String TMP_FILE_PREFIX = ".fbuf";
	// prefix of temporary file generated by system
	private final static String TMP_FILE_SUFFIX = ".tmp";
	// suffix of temporary file generated by system
	private final static String TMP_FILE_MODE = "rw";
    
    private final static int EOF = Integer.MAX_VALUE; // EOF indicates that no more records will be written to buffer


	/**
	 *  Constructor of the DynamicRecordBuffer object
	 *
	 *@param  tmpFilePath     Name of the subdirectory where to create TMP files or
	 *      NULL (the system default will be used)
	 *@param  dataBufferSize  The size of internal in memory buffer - two
     *          buffers of exactly the same size are created - one for reading, one
     *          for writing. The size should be at least MAX_RECORD_SIZE+4
	 */
	public DynamicRecordBuffer(String tmpFilePath, int dataBufferSize) {
		this.tmpFilePath = tmpFilePath;
        this.dataBufferSize=dataBufferSize;
    }

    /**
     * Initializes the buffer. Must be called before any write or read operation
     * is performed.
     * 
     * @since 27.11.2006
     */
    public void init(){
        hasFile = false;
        isClosed=false;
        dataBufferSize=Math.max(dataBufferSize,Defaults.Record.MAX_RECORD_SIZE+LEN_SIZE_SPECIFIER);
        emptyFileBuffers=new LinkedList<DiskSlot>();
        fullFileBuffers=new LinkedList<DiskSlot>();
        readDataBuffer = ByteBuffer.allocateDirect(dataBufferSize);
        writeDataBuffer = ByteBuffer.allocateDirect(dataBufferSize);
        tmpDataRecord=ByteBuffer.allocateDirect(Defaults.Record.MAX_RECORD_SIZE);
        lastSlot=-1;
        awaitingData=true;
        bufferedRecords=new AtomicInteger(0);
        readDataBuffer.flip();
    }
    
	/**
	 * Constructor of the DynamicRecordBuffer with tmp file
     * created under java.io.tmpdir dir.
	 * @param dataBufferSize
	 */
	public DynamicRecordBuffer(int dataBufferSize){
	    this(System.getProperty("java.io.tmpdir"),dataBufferSize);
    }
    
    
	/**
	 *  Opens buffer, creates temporary file.
	 *
	 *@exception  IOException  Description of Exception
	 *@since                   September 17, 2002
	 */
	private void openTmpFile() throws IOException {
		if (tmpFilePath != null) {
			tmpFile = File.createTempFile(TMP_FILE_PREFIX, TMP_FILE_SUFFIX, new File(tmpFilePath));
		} else {
			tmpFile = File.createTempFile(TMP_FILE_PREFIX, TMP_FILE_SUFFIX);
		}
		tmpFile.deleteOnExit();
		// we want the temp file be deleted on exit
		tmpFileChannel = new RandomAccessFile(tmpFile, TMP_FILE_MODE).getChannel();
		hasFile=true;
	}


	/**
	 *  Closes buffer, removes temporary file (is exists)
	 *
	 *@exception  IOException  Description of Exception
	 *@since                   September 17, 2002
	 */
	public void close() throws IOException {
		isClosed=true;
        if (hasFile) {
			tmpFileChannel.close();
			if (!tmpFile.delete()) {
				throw new IOException("Can't delete TMP file: " + tmpFile.getAbsoluteFile());
			}
		}
		hasFile = false;
        fullFileBuffers=null;
        emptyFileBuffers=null;
        readDataBuffer=null;
        writeDataBuffer=null;
	}

	/**
	 *  Clears the buffer. Temp file (if it was created) remains
	 * unchanged size-wise
	 */
	public void clear() {
	    emptyFileBuffers.addAll(fullFileBuffers);
        fullFileBuffers.clear();
		readDataBuffer.clear();
        writeDataBuffer.clear();
        awaitingData=true;
        bufferedRecords.set(0);
        readDataBuffer.flip();
        
	}


	/**
	 *  Stores one data record into buffer.
	 *
	 *@param  record             ByteBuffer containing record's data
	 *@exception  IOException  In case of IO failure
	 *@since                   September 17, 2002
	 */
	public int writeRecord(ByteBuffer record) throws IOException {
		if(isClosed){
			throw new IOException("Buffer has been closed !");
		}
		
		int recordSize = record.remaining();

        if (writeDataBuffer.remaining() < recordSize + LEN_SIZE_SPECIFIER) {
            flushWriteBuffer();
        }
		try {
			//writeDataBuffer.putInt(recordSize);
            encodeLength(writeDataBuffer,recordSize);
			writeDataBuffer.put(record);
            bufferedRecords.incrementAndGet();
		} catch (BufferOverflowException ex) {
			throw new IOException("WriteBuffer is not big enough to accomodate data record !");
		}
        return recordSize;
	}

    
    /**
     *  Stores one data record into buffer.
     * 
     * @param record    data record to be written
     * @throws IOException
     * @since 27.11.2006
     */
    public int writeRecord(DataRecord record) throws IOException {
        if (isClosed) {
            throw new IOException("Buffer has been closed !");
        }

        tmpDataRecord.clear();
        try {
            record.serialize(tmpDataRecord);
        } catch (BufferOverflowException ex) {
            throw new IOException(
                    "Internal buffer is not big enough to accomodate data record ! (See MAX_RECORD_SIZE parameter)");
        }
        tmpDataRecord.flip();
        int length = tmpDataRecord.remaining();

        if (writeDataBuffer.remaining() < length + LEN_SIZE_SPECIFIER) {
            flushWriteBuffer();
        }
        try {
            // writeDataBuffer.putInt(recordSize);
            encodeLength(writeDataBuffer, length);
            writeDataBuffer.put(tmpDataRecord);
            bufferedRecords.incrementAndGet();
        } catch (BufferOverflowException ex) {
            throw new IOException(
                    "WriteBuffer is not big enough to accomodate data record !");
        }
        return length;
    }
    
    
    /**
     * Indicates that there will be no more records written.
     * 
     * @throws IOException
     * @since 27.11.2006
     */
    public void setEOF() throws IOException {
        if(isClosed){
            throw new IOException("Buffer has been closed !");
        }
        if (writeDataBuffer.remaining() < LEN_SIZE_SPECIFIER){
            flushWriteBuffer();
        }
        //writeDataBuffer.putInt(EOF);
        encodeLength(writeDataBuffer,EOF);
        flushWriteBuffer();
        
    }
    

	/**
	 *  Secures that in memory buffer is "mapped" from proper location and
	 *  populated with data from TMP file (is needed)
	 *
	 *@param  position         Description of the Parameter
	 *@param  requestedSize    Description of the Parameter
	 *@exception  IOException  Description of the Exception
	 */
	private final synchronized void flushWriteBuffer() throws IOException {
            // we need to swap data - first try directly read buffer
            if (awaitingData) {
                // swap write & read buffer
                writeDataBuffer.flip();
                readDataBuffer.clear();
                readDataBuffer.put(writeDataBuffer);
                readDataBuffer.flip();
                writeDataBuffer.clear();
                awaitingData = false;
                notify();
            } else {

                if (!hasFile)
                    openTmpFile();
                DiskSlot diskSlot;
                // we need to get new buffer slot and save the current to disk
                if (emptyFileBuffers.size() > 0) {
                    diskSlot = emptyFileBuffers.removeFirst();
                } else {
                    diskSlot = new DiskSlot(++lastSlot);
                }
                writeDataBuffer.flip();
                diskSlot.setUsedBytes(writeDataBuffer.limit());
                // set full limit to data buffer - want to
                // save full buffer (even if it is not fully populated - for performance 
                // reasons
                writeDataBuffer.limit(dataBufferSize);
                tmpFileChannel.write(writeDataBuffer, diskSlot.getPosition(dataBufferSize));
                writeDataBuffer.clear();
                fullFileBuffers.add(diskSlot);
            }
	}



	/**
	 *  Reads next record from the buffer - FIFO order.
	 *
	 *@param  record             ByteBuffer into which store data
	 *@return                  true if successfull, otherwise false - meaning no more
     *records in buffer (EOF)
	 *@exception  IOException  Description of the Exception
	 */
	public boolean readRecod(ByteBuffer record) throws IOException {
		if(isClosed){
			throw new IOException("Buffer has been closed !");
		}

        // test that we have enough data
        if (readDataBuffer.remaining()<LEN_SIZE_SPECIFIER){
            secureReadBuffer();
            
        }
        //int recordSize = readDataBuffer.getInt();
        int recordSize= decodeLength(readDataBuffer);
        if (recordSize==EOF){
            close();
            return false;
        }
        
        int oldLimit = readDataBuffer.limit();
        readDataBuffer.limit(readDataBuffer.position() + recordSize);
        record.clear();
        record.put(readDataBuffer);
        readDataBuffer.limit(oldLimit);
        record.flip();
        bufferedRecords.decrementAndGet();
        return true;
	}

    
    /**
     * Reads next record from the buffer - FIFO order.
     * 
     * @param record record into which store data
     * @return  record populated with data or NULL if no more records in buffer (EOF)
     * @throws IOException
     * @since 27.11.2006
     */
    public DataRecord readRecord(DataRecord record) throws IOException{
        if(isClosed){
            throw new IOException("Buffer has been closed !");
        }

        // test that we have enough data
        if (readDataBuffer.remaining()<LEN_SIZE_SPECIFIER){
            secureReadBuffer();
            
        }
        //int recordSize = readDataBuffer.getInt();
        int recordSize= decodeLength(readDataBuffer);
        if (recordSize==EOF){
            close();
            return null;
        }
            
        record.deserialize(readDataBuffer);
        bufferedRecords.decrementAndGet();
        return record;
        
        
    }

    private final synchronized void secureReadBuffer() throws IOException{
        // is there a save data buffer already ?
        if (fullFileBuffers.size()>0){
            DiskSlot slot=fullFileBuffers.removeFirst();
            readDataBuffer.clear();
            tmpFileChannel.read(readDataBuffer, (long)slot.getSlot()*dataBufferSize);
            readDataBuffer.flip();
            readDataBuffer.limit(slot.getUsedBytes());
            emptyFileBuffers.add(slot);
        }else{ // we may read it from    writeBuffer
            // set flag that we are waiting for writer..
            awaitingData=true;
            while(awaitingData){
                notify();
                try{
                    wait();
                }catch(InterruptedException ex){
                    throw new IOException("Interrupted when waiting for full buffer: "+
                            ex.getMessage());
                }
            }
        }
    }
    

	/**
     * Checks status of the buffer
     *  
	 * @return     true if buffer is empty (contains no records) or false
	 * @since 27.11.2006
	 */
	public boolean isEmpty(){
		return !(writeDataBuffer.hasRemaining() || readDataBuffer.hasRemaining() ||
            fullFileBuffers.size()>0);
	}
    
    /**
     * Checks wheter readRecord operation would return data without blocking (
     * waiting for record to be written to buffer)
     * 
     * @return  true if data is ready to be read, otherwise false
     * @since 27.11.2006
     */
    public boolean hasData(){
        return readDataBuffer.hasRemaining() ||
        fullFileBuffers.size()>0;
    }


    /**
     * Checks whether this buffer alrady allocated TMP file for
     * swapping buffer's content.
     * 
     * @return the hasFile
     * @since 20.11.2006
     */
    public boolean isHasFile() {
        return hasFile;
    }


    /**
     * Determines number of records currently stored in buffer
     * 
     * @return number of records currently stored in buffer
     * @since 20.11.2006
     */
    public int getBufferedRecords() {
        return bufferedRecords.get();
    }
    
    private static class DiskSlot {
        int slot;
        int usedBytes;
        
         DiskSlot(int slot,int usedBytes){
            this.slot=slot;
            this.usedBytes=usedBytes;
        }


         DiskSlot(int slot){
             this(slot,-1);
         }
         
        /**
         * @return the slot
         * @since 21.11.2006
         */
         int getSlot() {
            return slot;
        }

        /**
         * @param slot the slot to set
         * @since 21.11.2006
         */
         void setSlot(int slot) {
            this.slot = slot;
        }

        /**
         * @return the usedBytes
         * @since 21.11.2006
         */
         int getUsedBytes() {
            return usedBytes;
        }

         /**
         * @param bufferSize
         * @return
         * @since 27.11.2006
         */
        long getPosition(final int bufferSize){
             final long position=slot;
             return position * bufferSize;
         }
         
        /**
         * @param usedBytes the usedBytes to set
         * @since 21.11.2006
         */
         void setUsedBytes(int usedBytes) {
            this.usedBytes = usedBytes;
        }
        
    }
    
}

