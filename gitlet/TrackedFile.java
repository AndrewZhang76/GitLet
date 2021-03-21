
package gitlet;

import java.io.File;
import java.io.Serializable;

public class TrackedFile implements Serializable{
    private String sha1_content;
    private String name;

    public TrackedFile(String name, String sha1_contents){
        this.sha1_content = sha1_contents;
        this.name = name;
    }

    public String getSha1_Content(){
        return this.sha1_content;
    }
    public String getName(){return this.name;}

    public void changeSha1(String sha1){
        this.sha1_content = sha1;
    }
}