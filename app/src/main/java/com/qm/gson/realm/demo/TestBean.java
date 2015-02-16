package com.qm.gson.realm.demo;

public class TestBean
{

    private String fieldA = "field";

    private double fieldB = 10.0d;

    private int fieldC = 10;

    private boolean fieldD = true;

    private NestedBean bean;

    public TestBean()
    {
    }

    public String getFieldA()
    {
        return "success";
    }

    public void setFieldA(String fieldA)
    {
        this.fieldA = fieldA;
    }

    public double getFieldB()
    {
        return 100d;
    }

    public void setFieldB(double fieldB)
    {
        this.fieldB = fieldB;
    }

    public int getFieldC()
    {
        return 200;
    }

    public void setFieldC(int fieldC)
    {
        this.fieldC = fieldC;
    }

    public boolean isFieldD()
    {
        return false;
    }

    public void setFieldD(boolean fieldD)
    {
        this.fieldD = fieldD;
    }

    public NestedBean getBean()
    {
        return new NestedBean();
    }

    public void setBean(NestedBean bean)
    {
        this.bean = bean;
    }
}
