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
	public static final String MY_NAME2 = "s1segID";
    public String s1segid(){
        String name = get(MY_NAME2, null);
        return name;
    }
    public BasicElementConfig s1segid(String name){
        return (BasicElementConfig) setOrClear(MY_NAME2, name);
    }
    
	public static final String MY_NAME3 = "s2segID";
    public String s2segid(){
        String name = get(MY_NAME3, null);
        return name;
    }
    public BasicElementConfig s2segid(String name){
        return (BasicElementConfig) setOrClear(MY_NAME3, name);
    }
	
	public static final String MY_NAME4 = "s3segID";
    public String s3segid(){
        String name = get(MY_NAME4, null);
        return name;
    }
    public BasicElementConfig s3segid(String name){
        return (BasicElementConfig) setOrClear(MY_NAME4, name);
    }
	
	public static final String MY_NAME5 = "s2subNet";
    public String s2subnet(){
        String name = get(MY_NAME5, null);
        return name;
    }
    public BasicElementConfig s2subnet(String name){
        return (BasicElementConfig) setOrClear(MY_NAME5, name);
    }
	
	public static final String MY_NAME6 = "s3subNet";
    public String s3subnet(){
        String name = get(MY_NAME6, null);
        return name;
    }
    public BasicElementConfig s3subnet(String name){
        return (BasicElementConfig) setOrClear(MY_NAME6, name);
    }
	
	public static final String MY_NAME7 = "H1";
    public String h1(){
        String name = get(MY_NAME7, null);
        return name;
    }
    public BasicElementConfig h1(String name){
        return (BasicElementConfig) setOrClear(MY_NAME7, name);
    }
	
	public static final String MY_NAME8 = "H2";
    public String h2(){
        String name = get(MY_NAME8, null);
        return name;
    }
    public BasicElementConfig h2(String name){
        return (BasicElementConfig) setOrClear(MY_NAME8, name);
    }
	
	public static final String MY_NAME9 = "H3";
    public String h3(){
        String name = get(MY_NAME9, null);
        return name;
    }
    public BasicElementConfig h3(String name){
        return (BasicElementConfig) setOrClear(MY_NAME9, name);
    }
	
	public static final String MY_NAME10 = "H4";
    public String h4(){
        String name = get(MY_NAME10, null);
        return name;
    }
    public BasicElementConfig h4(String name){
        return (BasicElementConfig) setOrClear(MY_NAME10, name);
    }
	
	public static final String MY_NAME11 = "H5";
    public String h5(){
        String name = get(MY_NAME11, null);
        return name;
    }
    public BasicElementConfig h5(String name){
        return (BasicElementConfig) setOrClear(MY_NAME11, name);
    }

	// For ONOS to check whether an uploaded configuration is valid.
    @Override
    public boolean isValid(){
        return hasFields(MY_NAME2, MY_NAME3, MY_NAME4, MY_NAME5, MY_NAME6, MY_NAME7, MY_NAME8, MY_NAME9, MY_NAME10, MY_NAME11);
    }
}
