/*
 * jETeL/Clover - Java based ETL application framework.
 * Copyright (c) Opensys TM by Javlin, a.s. (www.opensys.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU   
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */
package org.jetel.component;

import java.util.Properties;

import org.jetel.ctl.CTLAbstractTransform;
import org.jetel.ctl.CTLEntryPoint;
import org.jetel.data.DataRecord;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.TransformException;
import org.jetel.metadata.DataRecordMetadata;

/**
 * Base class of all Java transforms generated by CTL-to-Java compiler from CTL transforms in Reformat-like and
 * Joiner-like components.
 *
 * @author Michal Tomcanyi, Javlin a.s. &lt;michal.tomcanyi@javlin.cz&gt;
 * @author Martin Janik, Javlin a.s. &lt;martin.janik@javlin.eu&gt;
 *
 * @version 5th May 2010
 * @created 28th April 2009
 *
 * @see RecordTransform
 */
public abstract class CTLRecordTransform extends CTLAbstractTransform implements RecordTransform {

	public final boolean init(Properties parameters, DataRecordMetadata[] sourceRecordsMetadata,
			DataRecordMetadata[] targetRecordsMetadata) throws ComponentNotReadyException {
		// arrays of both input and output data records are provided via method call

		globalScopeInit();

		return initDelegate();
	}

	/**
	 * Called by {@link #init(Properties, DataRecordMetadata[], DataRecordMetadata[])} to perform user-specific
	 * initialization defined in the CTL transform. The default implementation does nothing, may be overridden
	 * by the generated transform class.
	 *
	 * @return <code>true</code> on success, <code>false</code> otherwise
	 *
	 * @throws ComponentNotReadyException if the initialization fails
	 */
	@CTLEntryPoint(name = "init", required = false)
	protected boolean initDelegate() throws ComponentNotReadyException {
		// does nothing and succeeds by default, may be overridden by generated transform classes
		return true;
	}

	public final int transform(DataRecord[] sources, DataRecord[] targets) throws TransformException {
		inputRecords = sources;
		outputRecords = targets;

		try {
			return transformDelegate();
		} catch (ComponentNotReadyException exception) {
			// the exception may be thrown by lookups, sequences, etc.
			throw new TransformException("Generated transform class threw an exception!", exception);
		}
	}

	/**
	 * Called by {@link #transform(DataRecord[], DataRecord[])} to transform data records in a user-specific way
	 * defined in the CTL transform. Has to be overridden by the generated transform class.
	 *
	 * @throws ComponentNotReadyException if some internal initialization failed
	 * @throws TransformException if an error occurred
	 */
	@CTLEntryPoint(name = "transform", required = true)
	protected abstract int transformDelegate() throws ComponentNotReadyException, TransformException;

	public final void signal(Object signalObject) {
		// does nothing
	}

	@CTLEntryPoint(name = "getSemiResult", required = false)
	public Object getSemiResult() {
		// null by default, may be overridden by generated transform classes
		return null;
	}

	@CTLEntryPoint(name = "getMessage", required = false)
	public String getMessage() {
		// null by default, may be overridden by generated transform classes
		return null;
	}

	@CTLEntryPoint(name = "finished", required = false)
	public void finished() {
		// does nothing by default, may be overridden by generated transform classes
	}

	@CTLEntryPoint(name = "reset", required = false)
	public void reset() {
		// does nothing by default, may be overridden by generated transform classes
	}

}
