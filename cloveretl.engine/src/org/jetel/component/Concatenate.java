/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2002  David Pavlis
*
*    This program is free software; you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation; either version 2 of the License, or
*    (at your option) any later version.
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package org.jetel.component;
import java.util.*;
import java.io.*;
import org.jetel.graph.*;
import org.jetel.data.DataRecord;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.util.ComponentXMLAttributes;

/**
 * <h3>Concatenate Component</h3>
 *
 * <!-- All records from all input ports are copied onto output port [0]
 *  It goes port by port (waiting/blocked) if there is currently no data on port.
 *  When reading from one port is done (EOF status), continues with the next -->
 *
 * <table border="1">
 *  <th>Component:</th>
 * <tr><td><h4><i>Name:</i></h4></td>
 * <td>Concatenate</td></tr>
 * <tr><td><h4><i>Category:</i></h4></td>
 * <td></td></tr>
 * <tr><td><h4><i>Description:</i></h4></td>
 * <td>All records from all input ports are copied onto output port [0].<br>
 *  It goes port by port (waiting/blocked) if there is currently no data on port.<br>
 *  When reading from one port is done (EOF status), continues with the next.</td></tr>
 * <tr><td><h4><i>Inputs:</i></h4></td>
 * <td>At least one input port defined/connected</td></tr>
 * <tr><td><h4><i>Outputs:</i></h4></td>
 * <td>Output port[0] defined/connected.</td></tr>
 * <tr><td><h4><i>Comment:</i></h4></td>
 * <td></td></tr>
 * </table>
 *  <br>  
 *  <table border="1">
 *  <th>XML attributes:</th>
 *  <tr><td><b>type</b></td><td>"CONCATENATE"</td></tr>
 *  <tr><td><b>id</b></td><td>component identification</td>
 *  </tr>
 *  </table>  
 *
 * @author     dpavlis
 * @since    April 4, 2002
 */
public class Concatenate extends Node {

	public final static String COMPONENT_TYPE = "CONCATENATE";
	/*
	 *  not needed as record gets read from all defined input ports
	 *  private static final int READ_FROM_PORT=0;
	 */

	private final static int WRITE_TO_PORT = 0;


	/**
	 *Constructor for the Concatenate object
	 *
	 * @param  id  Description of Parameter
	 * @since      May 21, 2002
	 */
	public Concatenate(String id) {
		super(id);

	}


	/**
	 *  Gets the Type attribute of the SimpleCopy object
	 *
	 * @return    The Type value
	 * @since     April 4, 2002
	 */
	public String getType() {
		return COMPONENT_TYPE;
	}


	/**
	 *  Main processing method for the SimpleCopy object
	 *
	 * @since    April 4, 2002
	 */
	public void run() {
		// the metadata is taken from output port definition
		Iterator iterator;
		OutputPort outPort=getOutputPort(WRITE_TO_PORT);
		DataRecord record = new DataRecord(outPort.getMetadata());
		DataRecord inRecord;
		record.init();
		InputPort inPort;
		Collection inputPorts=getInPorts(); // keep it locally
		
		
		iterator=inputPorts.iterator();
		int counter=0;
	
		// till we have some port
		while(iterator.hasNext() && runIt){
			
			inPort=(InputPort)iterator.next();
			
			while(runIt){
				try {
					inRecord = inPort.readRecord(record);
					if (inRecord != null) {
						outPort.writeRecord(inRecord);
					}else{
						break;
					}
				}
				catch (IOException ex) {
					resultMsg=ex.getMessage();
					resultCode=Node.RESULT_ERROR;
					closeAllOutputPorts();
					return;
				}
				catch (Exception ex) {
					resultMsg=ex.getMessage();
					resultCode=Node.RESULT_FATAL_ERROR;
					return;
				}
			}
		}
		
		setEOF(WRITE_TO_PORT);
		if (runIt) resultMsg="OK"; else resultMsg="STOPPED";
		resultCode=Node.RESULT_OK;
	}


	/**
	 *  Description of the Method
	 *
	 * @exception  ComponentNotReadyException  Description of Exception
	 * @since                                  April 4, 2002
	 */
	public void init() throws ComponentNotReadyException {
		// test that we have at least one input port and one output
		if (inPorts.size() < 1) {
			throw new ComponentNotReadyException("At least one input port has to be defined!");
		} else if (outPorts.size() < 1) {
			throw new ComponentNotReadyException("At least one output port has to be defined!");
		}
	}


	/**
	 *  Description of the Method
	 *
	 * @return    Description of the Returned Value
	 * @since     May 21, 2002
	 */
	public org.w3c.dom.Node toXML() {
		// TODO
		return null;
	}


	/**
	 *  Description of the Method
	 *
	 * @param  nodeXML  Description of Parameter
	 * @return          Description of the Returned Value
	 * @since           May 21, 2002
	 */
	public static Node fromXML(org.w3c.dom.Node nodeXML) {
		ComponentXMLAttributes xattribs=new ComponentXMLAttributes(nodeXML);

		try{
			return new Concatenate(xattribs.getString("id"));
		}catch(Exception ex){
			System.err.println(ex.getMessage());
			return null;
		}
	}
}

