package org.leng.utils;

import org.leng.Lengbanlist;

import java.io.IOException;
import java.util.List;

public class ConfigUtils {
    public static void addValueToList(String listname,String newValue) {
        List<String> myList = Lengbanlist.getInstance().getConfig().getStringList(listname);
        myList.add(newValue);
        Lengbanlist.getInstance().getConfig().set(listname, myList);
        try {
            Lengbanlist.getInstance().getConfig().save(Lengbanlist.getInstance().getConfig().getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void removeValueToList(String listname,String value) {
        List<String> myList = Lengbanlist.getInstance().getConfig().getStringList(listname);
        myList.remove(value);
        Lengbanlist.getInstance().getConfig().set(listname, myList);
        try {
            Lengbanlist.getInstance().getConfig().save(Lengbanlist.getInstance().getConfig().getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
