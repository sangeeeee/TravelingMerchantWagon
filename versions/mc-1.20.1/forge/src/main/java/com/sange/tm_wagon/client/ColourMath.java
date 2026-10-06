package com.sange.tm_wagon.client;
public final class ColourMath {
    public static int multiply(int a,int b) {return ((a>>>24)*(b>>>24)/255<<24)|(((a>>>16)&255)*((b>>>16)&255)/255<<16)|(((a>>>8)&255)*((b>>>8)&255)/255<<8)|((a&255)*(b&255)/255);}
    private ColourMath() {}
    public static int pack(float r,float g,float b,float a) {return ((int)(a*255)<<24)|((int)(r*255)<<16)|((int)(g*255)<<8)|(int)(b*255);}
}
