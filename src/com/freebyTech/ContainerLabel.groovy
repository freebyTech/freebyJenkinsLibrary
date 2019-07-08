package com.freebyTech

import com.freebyTech.BuildConstants

class ContainerLabel implements Serializable {
    String label

    ContainerLabel(String opDescription, String image) 
    { 
        String label = "${opDescription}-${image}-${UUID.randomUUID().toString()}"
        if(label.length() > 63) 
        {
            label = label.substring(0, 63)
        }
    }    
}