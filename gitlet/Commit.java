package gitlet;

import jdk.jshell.execution.Util;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.sql.Timestamp;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public class Commit implements Serializable{
    private String parent; //the sha1 hash of the parent commit.
    private HashMap<String, String> files; //the array of all files in this commit.
    private String time; //the timestamp of a commit;
    private String message;
    private String sha1Code;
    private String parent2 = null; //the sha1 code for ancestor

    /** Constructor for initial commit */
    public Commit(){
        this.parent = null;
        this.files = null;
        this.time = "Wed Dec 31 16:00:00 1969 -0800";
        this.message = "initial commit";
        this.sha1Code = Utils.sha1(message, time);
    }

    /** By default, each commit’s snapshot of files will be exactly the same as its parent commit’s snapshot of files */
    public Commit(String parent){
        File parentPath = new File(".gitlet/committing", parent);
        Commit parentCommit = Utils.readObject(parentPath, Commit.class);
        this.parent = parent;
        this.files = parentCommit.files;
        this.time = parentCommit.time;
        this.message = parentCommit.message;
        this.sha1Code = parentCommit.sha1Code;
    }

    public String setDate(){
        return this.time = ZonedDateTime.now().format(DateTimeFormatter
                .ofPattern("E MMM d HH:mm:ss uuuu xxxx", new Locale("en", "US")));
    }
    /** getter method */
    public String getParent(){return this.parent;}

    public HashMap<String, String> getFiles(){return this.files;}

    public String getTime(){return this.time;}

    public String getMessage(){return this.message;}

    public String getSha1Code(){return this.sha1Code;}

    public String getParent2(){return this.parent2;}

    /** setter method */
    public void setSha1Code(String sha1_content) {
        this.sha1Code = sha1_content;
    }

    public void setParent2(String sha1){this.parent2 = sha1;}

    public void setTime(){this.time = setDate();}

    public void setMessage(String message){this.message = message;}

    public void setFiles(HashMap<String, String> files) { this.files = files;}


    public void printLog() {
        System.out.println("===");
        System.out.println("commit " + this.sha1Code);
        System.out.println("Date: " + this.time);
        System.out.println(this.message);
        System.out.println();

    }
}