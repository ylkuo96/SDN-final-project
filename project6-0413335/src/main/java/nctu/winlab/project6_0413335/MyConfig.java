/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.project6_0413335;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.basics.BasicElementConfig;


/**
 * My Config class.
 */
public class MyConfig extends Config<ApplicationId>{

    // The JSON file should contain one field "dhcpServer".
    public static final String MY_NAME = "dhcpServer";

    // To retreat the value.
    public String dhcpserver(){
        String name = get(MY_NAME, null);
        return name;
    }

    // To set or clear the value.
    public BasicElementConfig dhcpserver(String name){
        return (BasicElementConfig) setOrClear(MY_NAME, name);
    }
	
	/*
	// For ONOS to check whether an uploaded configuration is valid.
    @Override
    public boolean isValid(){
        return hasOnlyFields(MY_NAME);
    }
	*/
    
	/* --- */
	public static final String MY_NAME2 = "demo";
    public String myname2(){
        String name = get(MY_NAME2, null);
        return name;
    }

    public BasicElementConfig myname2(String name){
        return (BasicElementConfig) setOrClear(MY_NAME2, name);
    }
    
	// For ONOS to check whether an uploaded configuration is valid.
    @Override
    public boolean isValid(){
        return hasFields(MY_NAME, MY_NAME2);
    }
	/* --- */
}
