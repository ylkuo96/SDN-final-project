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
package nctu.winlab.project8_0413335;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.basics.BasicElementConfig;


/**
 * My Config class.
 */
public class MyConfig extends Config<ApplicationId>{
	public static final String tmp2 = "Switches";
    public String switches(){
        String name = get(tmp2, null);
        return name;
    }
    public BasicElementConfig switches(String name){
        return (BasicElementConfig) setOrClear(tmp2, name);
    }

	public static final String tmp3 = "Hosts";
    public String hosts(){
        String name = get(tmp3, null);
        return name;
    }
    public BasicElementConfig hosts(String name){
        return (BasicElementConfig) setOrClear(tmp3, name);
    }

    @Override
    public boolean isValid(){
        return hasFields(tmp2, tmp3);
    }
}
