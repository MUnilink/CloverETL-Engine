/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2005-06  Javlin Consulting <info@javlinconsulting.cz>
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
package org.jetel.plugin;

import org.jetel.util.string.StringUtils;

/**
 * This class represents plugin prerequisite. Plugin depends on plugin defined by this class.
 * 
 * @author Martin Zatopek
 *
 */
class PluginPrerequisite {
    
    String pluginId;
    String pluginVersion;
    String match;
    
    public PluginPrerequisite(String pluginId, String pluginVersion, String match) {
        this.pluginId = pluginId;
        this.pluginVersion = StringUtils.isEmpty(pluginVersion) ? null : pluginVersion;
        this.match = StringUtils.isEmpty(match) ? null : match;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }
}
