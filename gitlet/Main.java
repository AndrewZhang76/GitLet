package gitlet;

import jdk.jshell.execution.Util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.security.AlgorithmConstraints;
import java.util.*;

import static java.nio.file.StandardCopyOption.*;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Ricardo Mo
 */
public class Main {
    public static final File CWD = new File(".");
    public static final File GITLET = new File(CWD,".gitlet");
    public static final File stagingArea = new File(GITLET, "staging");
    public static final File committingArea = new File(GITLET, "committing");
    public static final File blobsArea = new File(GITLET, "blobs");
    public static final File addition = new File(stagingArea, "addition");
    public static final File removal = new File(stagingArea, "removal");
    public static final File HEAD = new File(GITLET, "HEAD");
    public static final File MASTER = new File(GITLET, "master");
    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String[] args) throws IOException {
        if(args.length == 0){
            exitWithError("Please enter a command");
        }
        switch (args[0]){
            case "init":
                initialization(args);
                break;
            case "log":
                gitlog(args);
                break;
            case "add":
                addFileToStaged(args);
                break;
            case "commit":
                makeCommit(args);
                break;
            case "rm":
                rm(args);
                break;
            case "branch":
                makeBranch(args);
                break;
            case "rm-branch":
                removeBranch(args);
                break;
            case "checkout":
                checkout(args);
                break;
            case "global-log":
                globalLog();
                break;
            case "find":
                if (args.length==2) {
                    find(args[1]);
                    break;
                } else {
                    exitWithError("Incorrect operands");
                }
            case "status":
                if (args.length==1) {
                    Status();
                    break;
                } else {
                    exitWithError("Incorrect operands");
                }
            case "reset":
                reset(args);
                break;
            case "merge":
                if(args.length != 2){
                    exitWithError("Incorrect operands.");
                }
                merge(args[1]);
                break;
            default:
                exitWithError("No command with that name exists.");
        }
    }

    public static void initialization(String[] args) throws IOException {
        if(args.length != 1){
            exitWithError("Incorrect operands.");
        }
        if(GITLET.exists()){
            exitWithError("A Gitlet version-control system already exists in the current directory");
        }
        GITLET.mkdir();
        stagingArea.mkdir();
        committingArea.mkdir();
        blobsArea.mkdir();
        //the first
        //.createNewFile();
        //Utils.writeObject(addition ,new HashMap<String, String>());
        Commit initial = new Commit();
        String sha1_initial = Utils.sha1(Utils.serialize(initial));
        initial.setSha1Code(sha1_initial);
        File initial_commit = new File(committingArea, sha1_initial);
        Utils.writeContents(initial_commit, Utils.serialize(initial));
        HEAD.createNewFile();
        MASTER.createNewFile();
        Utils.writeContents(MASTER, sha1_initial);
        Utils.writeContents(HEAD, "master");

    }

    public static void addFileToStaged(String[] args) throws IOException {
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        if(args.length != 2){
            exitWithError("Incorrect operands.");
        }
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        String filename = args[1];
        File path = new File(CWD, filename);
        if(!path.exists()){
            exitWithError("File does not exist.");
        }
        if(!addition.exists()){
            addition.createNewFile();
            HashMap<String, String> emptyAdd = new HashMap<>();
            Utils.writeObject(addition ,emptyAdd);
        }
        String sha1_content = Utils.sha1(Utils.readContents(path)); // sha1-hash of the content;
        HashMap currStaged = Utils.readObject(addition, HashMap.class);//files staged in staging area;
        Commit curr = getHEAD();// the commit stored in HEAD, e.g. the current commit
        if(currStaged.containsKey(filename) && currStaged.get(filename) != sha1_content){//check if the file is already staged
            currStaged.replace(filename, sha1_content);
            File blob = new File(blobsArea, sha1_content);
            Utils.writeContents(blob, Utils.readContents(path));
        }
        if(curr.getFiles() != null) {
            if (curr.getFiles().containsKey(filename) && curr.getFiles().get(filename).equals(sha1_content)) {
                currStaged.remove(filename, sha1_content);
            }
            else{
                currStaged.put(filename, sha1_content);
                File blob = new File(blobsArea, sha1_content);
                Utils.writeContents(blob, Utils.readContents(path));
            }
        } else{
            currStaged.put(filename, sha1_content);
            File blob = new File(blobsArea, sha1_content);
            Utils.writeContents(blob, Utils.readContents(path));
        }
        if(removal.exists()){ //if the file is currently staged in removal, remove it from the removal stage
            HashMap removalStage = Utils.readObject(removal, HashMap.class);
            if(removalStage.containsKey(filename)){
                removalStage.remove(filename);
                Utils.writeObject(removal, removalStage);
            }
            if(Utils.readObject(removal, HashMap.class).size() == 0){// if staging area has 0 element, delete the staging file
                removal.delete();
            }
        }
        Utils.writeObject(addition, currStaged);
        if(currStaged.isEmpty()){
            addition.delete();
        }
    }

    public static void makeCommit(String[] args) throws IOException {
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        if(args.length > 2){
            exitWithError("Incorrect operands.");
        }
        if(args.length == 1){
            exitWithError("Please enter a commit message.");
        }
        if(args[1].equals("")){
            exitWithError("Please enter a commit message.");
        }
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        if(!addition.exists() && !removal.exists()){
            exitWithError("No changes added to the commit.");
        }

        String message = args[1]; //commit message
        Commit curr = getHEAD();//the Head commit
        Commit newCommit = new Commit(curr.getSha1Code()); //create a new commit inherited from its parent, the HEAD;
        newCommit.setDate();//set new Date
        newCommit.setMessage(message);
        if(!addition.exists()){
            addition.createNewFile();
            HashMap<String, String> emptyAdd = new HashMap<>();
            Utils.writeObject(addition ,emptyAdd);
        }
        HashMap stagedAddList = Utils.readObject(addition, HashMap.class);//map of stagedAdd Files
        if(curr.getFiles() != null){//when the parent commit is not initial commit
            HashMap committedList = (HashMap) curr.getFiles().clone();
            for(String key : (Set<String>) committedList.keySet()){
                if(!stagedAddList.containsKey(key)){
                    stagedAddList.put(key, committedList.get(key));
                }
            }
        }
        if(removal.exists()){ //untrack files in removal stage
            HashMap StagedRemoveList = Utils.readObject(removal, HashMap.class);
            for(String key: (Set<String>) StagedRemoveList.keySet()){
                if(stagedAddList.containsKey(key)){
                    stagedAddList.remove(key);
                }
            }
        }
        newCommit.setFiles(stagedAddList);
        /** store the commit into a file*/
        String sha1_commit = Utils.sha1(Utils.serialize(newCommit));
        newCommit.setSha1Code(sha1_commit);
        File currCommit = new File(committingArea, sha1_commit);
        currCommit.createNewFile();
        Utils.writeObject(currCommit, newCommit);
        Utils.writeContents(Utils.join(GITLET, Utils.readContentsAsString(HEAD)), sha1_commit); // update the head commit in current branch
        /** clean the staging area */
        addition.delete();
        removal.delete();
    }
    public static void gitlog(String[] args){
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        if(args.length != 1){
            exitWithError("Incorrect operands.");
        }
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        Commit curr = getHEAD(); //The HEAD commit
        File parents = null;
        while(curr.getParent()!=null){
            curr.printLog();
            parents = new File(committingArea,curr.getParent());
            curr=Utils.readObject(parents, Commit.class);
        }
        curr.printLog();
    }

    public static void globalLog() {
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        List<String> allCommit = gitlet.Utils.plainFilenamesIn(committingArea);
        Collections.sort(allCommit);
        for (String id : allCommit){
            gitlet.Commit temp = gitlet.Utils.readObject(gitlet.Utils.join(committingArea, id), gitlet.Commit.class);
            temp.printLog();
        }
    }

    public static void find (String message){
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        List<String> allCommit = gitlet.Utils.plainFilenamesIn(committingArea);
        List<String> list = new ArrayList<>();
        for (String id : allCommit) {
            gitlet.Commit commit = gitlet.Utils.readObject(gitlet.Utils.join(committingArea, id), gitlet.Commit.class);
            if (message.equals(commit.getMessage())){
                list.add(id);
            }
        }
        if (list.isEmpty()){
            exitWithError("Found no commit with that message.");
        } else {
            for (String id : list) {
                gitlet.Utils.message(id);
            }
        }
    }
    public static void rm(String[] args) throws IOException {
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        if(args.length != 2){
            exitWithError("Incorrect operands.");
        }
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        String filename = args[1];
        Commit curr = getHEAD(); //the HEAD commit
        if (addition.exists()) {
            HashMap currStaged = Utils.readObject(addition, HashMap.class);
            if (!currStaged.containsKey(filename) && (curr.getFiles() == null || !curr.getFiles().containsKey(filename))) {
                exitWithError("No reason to remove the file.");
            }
            if (currStaged.containsKey(filename)) {
                currStaged.remove(filename);
                Utils.writeObject(addition, currStaged);
            }
            if(Utils.readObject(addition, HashMap.class).size() == 0){ // if staging area has 0 element, delete the staging file
                addition.delete();
            }
        } else { // no file in staging for addition
            if (curr.getFiles() == null || !curr.getFiles().containsKey(filename)) {
                exitWithError("No reason to remove the file.");
            }
        }
        if(curr.getFiles() != null && curr.getFiles().containsKey(filename)){
            if(!removal.exists()){ //create the staging for removal
                removal.createNewFile();
                HashMap<String, String> emptyAdd = new HashMap<>();
                Utils.writeObject(removal ,emptyAdd);
            }
            HashMap currstage = Utils.readObject(removal, HashMap.class);
            currstage.put(filename,curr.getFiles().get(filename));
            Utils.writeObject(removal, currstage);
            Utils.restrictedDelete(filename);
        }
    }

    public static void makeBranch(String[] args) throws IOException {
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        if(args.length != 2){
            exitWithError("Incorrect operands.");
        }
        File branchName = new File(GITLET, args[1]);
        if(branchName.exists()){
            exitWithError("A branch with that name already exists.");
        }
        branchName.createNewFile();
        Commit curr = getHEAD();
        String head_sha1 = curr.getSha1Code();
        Utils.writeContents(branchName, head_sha1);
    }

    public static void removeBranch(String[] args){
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        if(args.length != 2){
            exitWithError("Incorrect operands");
        }
        File branchName = new File(GITLET, args[1]);
        if(!branchName.exists()){
            exitWithError("A branch with that name does not exist.");
        }
        if(args[1].equals(Utils.readContentsAsString(HEAD))){
            exitWithError("Cannot remove the current branch.");
        }
        branchName.delete();
    }

    public static void checkout(String[] args) throws IOException {
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        if(args.length == 2){ //checkout branch
            List branchList = Utils.plainFilenamesIn(GITLET);
            if(!branchList.contains(args[1])){ //failure case
                exitWithError("No such branch exists.");
            }
            if(Utils.readContentsAsString(HEAD).equals(args[1])){ //failure case
                exitWithError("No need to checkout the current branch.");
            }
            Commit currCommit = getHEAD();
            String branchCommitID = Utils.readContentsAsString(Utils.join(GITLET, args[1]));
            Commit branchCommit = Utils.readObject(Utils.join(committingArea, branchCommitID), Commit.class);
            if(branchCommit.getFiles() != null) {
                for (String filename : branchCommit.getFiles().keySet()) { //failure case
                    File workingPath = Utils.join(CWD, filename);
                    if (workingPath.exists() && (currCommit.getFiles() == null || !currCommit.getFiles().containsKey(filename))) { //failure case
                        exitWithError("There is an untracked file in the way; delete it, or add and commit it first.");
                    }
                }
            }
            /** delete the file that tracked in current commit but not in the checkout branch*/
            if(currCommit.getFiles() != null) {
                for (String filename : currCommit.getFiles().keySet()) {
                    File workingPath = Utils.join(CWD, filename);
                    if (workingPath.exists() && (branchCommit.getFiles() == null || !branchCommit.getFiles().containsKey(filename))) {
                        workingPath.delete();
                    }
                }
            }
            /** copy, overwrite */
            if(branchCommit.getFiles() != null) {
                for (String filename : branchCommit.getFiles().keySet()) {
                    File overwrite = Utils.join(CWD, filename);
                    if (!overwrite.exists()) {
                        overwrite.createNewFile();
                    }
                    File blob = Utils.join(blobsArea, branchCommit.getFiles().get(filename));
                    Utils.writeContents(overwrite, Utils.readContents(blob));
                }
            }
            removal.delete();
            addition.delete();
            Utils.writeContents(HEAD, args[1]);
        } else if(args.length == 3){ //checkout file in current commit
            if(!args[1].equals("--")){
                exitWithError("Incorrect operands.");
            }
            Commit currCommit = getHEAD();
            if (currCommit.getFiles() == null || !currCommit.getFiles().containsKey(args[2])) { //failure case
                exitWithError("File does not exist in that commit.");
            }
            File checkoutFile = Utils.join(CWD, args[2]);
            if(!checkoutFile.exists()){
                checkoutFile.createNewFile();
            }
            File blob = Utils.join(blobsArea, currCommit.getFiles().get(args[2]));
            Utils.writeContents(checkoutFile, Utils.readContents(blob));
        }else if(args.length == 4){ //checkout file in specific commit
            if(!args[2].equals("--")){
                exitWithError("Incorrect operands.");
            }
            /** shorten id */
            String commitInCommand = args[1];
            if(commitInCommand.length() < 40){
                for(String commitId: Utils.plainFilenamesIn(committingArea)){
                    if(shortenIdCheck(args[1], commitId)){
                        commitInCommand = commitId;
                        break;
                    }
                }
                if(commitInCommand.equals(args[1])){
                    exitWithError("No commit with that id exists.");
                }
            }

            List commitList = Utils.plainFilenamesIn(committingArea);
            if (!commitList.contains(commitInCommand)){ //failure case
                exitWithError("No commit with that id exists.");
            }
            Commit sCommit = Utils.readObject(Utils.join(committingArea, commitInCommand), Commit.class);
            if(sCommit.getFiles() != null) {
                if (!sCommit.getFiles().containsKey(args[3])) { //failure case
                    exitWithError("File does not exist in that commit.");
                }
                File checkoutFile = Utils.join(CWD, args[3]);
                if (!checkoutFile.exists()) {
                    checkoutFile.createNewFile();
                }
                File blob = Utils.join(blobsArea, sCommit.getFiles().get(args[3]));
                Utils.writeContents(checkoutFile, Utils.readContents(blob));
            }
        }else{
            exitWithError("Incorrect operands.");
        }
    }

    public static void Status() {
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        System.out.println("=== Branches ===");
        List<String> branchList = Utils.plainFilenamesIn(GITLET);
        Collections.sort(branchList);
        String curr = Utils.readContentsAsString(HEAD);
        for (String i : branchList) {
            if (!i.equals("HEAD")) {
                if (i.equals(curr)) {
                    System.out.println("*" + i);
                } else {
                    System.out.println(i);
                }
            }
        }
        System.out.println();
        System.out.println("=== Staged Files ===");
        if (addition.exists()) {
            HashMap<String, String> stagingList = Utils.readObject(addition, HashMap.class);
            List<String> list = new ArrayList<>();
            for (String i : stagingList.keySet()) {
                list.add(i);
            }
            Collections.sort(list);
            for (String i : list){
                System.out.println(i);
            }
        }
        System.out.println();
        System.out.println("=== Removed Files ===");
        if (removal.exists()) {
            HashMap<String, String> stagingList = Utils.readObject(removal, HashMap.class);
            List<String> list = new ArrayList<>();
            for (String i : stagingList.keySet()) {
                list.add(i);
            }
            Collections.sort(list);
            for (String i : list){
                System.out.println(i);
            }
        }
        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        Commit currCommit = getHEAD();
        HashMap<String, String> files = currCommit.getFiles();
        List<String> Modification = new ArrayList<>();
        if (files != null) {
            for (String i : files.keySet()) {
                File a = Utils.join(CWD, i);
                if (a.exists()) {
                    if (!files.get(i).equals(Utils.sha1(Utils.readContents(a)))) {
                        Modification.add(i + " (modified)");
                    }
                } else {
                    if (!removal.exists()) {
                        Modification.add(i + " (deleted)");
                    }
                }
            }
        }
        if (addition.exists()) {
            HashMap<String, String> add = Utils.readObject(addition, HashMap.class);
            List<String> AllFiles = Utils.plainFilenamesIn(CWD);
            for (String i : add.keySet()) {
                if (Utils.join(CWD, i).exists() && !add.get(i).equals(Utils.sha1(Utils.readContents(Utils.join(CWD, i))))) {
                    Modification.add(i + " (modified");
                }
                if (!Utils.join(CWD, i).exists()) {
                    Modification.add(i + " (deleted)");
                }
            }
        }
        Collections.sort(Modification);
        for (String name : Modification) {
            System.out.println(name);
        }
        System.out.println();
        System.out.println("=== Untracked Files ===");
        if (removal.exists()) {
            HashMap<String, String> remove = Utils.readObject(removal, HashMap.class);
            List<String> Untracked = new ArrayList<>();
            for (String i : remove.keySet()) {
                if (Utils.join(CWD, i).exists()) {
                    Untracked.add(i);
                }
            }
            Collections.sort(Untracked);
            for (String name : Untracked) {
                System.out.println(name);
            }
        }
        System.out.println();
    }

    public static void reset(String[] args) throws IOException {
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        if(args.length != 2){
            exitWithError("Incorrect operands.");
        }
        String commitInCommand = args[1];
        /** shorten id */
        if(commitInCommand.length() < 40){
            for(String commitId: Utils.plainFilenamesIn(committingArea)){
                if(shortenIdCheck(args[1], commitId)){
                    commitInCommand = commitId;
                    break;
                }
            }
            if(commitInCommand.equals(args[1])){
                exitWithError("No commit with that id exists.");
            }
        }else{
            List commitList = Utils.plainFilenamesIn(committingArea);
            if (!commitList.contains(commitInCommand)) { //failure case
                exitWithError("No commit with that id exists.");
            }
        }

        Commit currCommit = getHEAD();
        Commit sCommit = Utils.readObject(Utils.join(committingArea, commitInCommand), Commit.class);
        for(String filename: sCommit.getFiles().keySet()){ //failure case
            File workingPath = Utils.join(CWD, filename);
            if(workingPath.exists() && !currCommit.getFiles().containsKey(filename)){ //failure case
                exitWithError("There is an untracked file in the way; delete it, or add and commit it first.");
            }
        }
        /** delete the file that tracked in current commit but not in the checkout branch*/
        for(String filename: currCommit.getFiles().keySet()){
            File workingPath = Utils.join(CWD, filename);
            if(workingPath.exists() && !sCommit.getFiles().containsKey(filename)){
                workingPath.delete();
            }
        }
        /** copy, overwrite */
        for(String filename: sCommit.getFiles().keySet()){
            File overwrite = Utils.join(CWD, filename);
            if(!overwrite.exists()){ overwrite.createNewFile();}
            File blob = Utils.join(blobsArea, sCommit.getFiles().get(filename));
            Utils.writeContents(overwrite, Utils.readContents(blob));
        }
        /** update the head */
        File branchName = Utils.join(GITLET, Utils.readContentsAsString(HEAD));
        Utils.writeContents(branchName, commitInCommand);
        /** clear staging area */
        addition.delete();
        removal.delete();
    }

    public static void merge(String branchName) throws IOException {
        if(!GITLET.exists()){
            exitWithError("Not in an initialized Gitlet directory.");
        }
        boolean conflict = false;
        if (addition.exists() || removal.exists()) { //failure case
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }
        if (branchName.equals(Utils.readContentsAsString(HEAD))) { //failure case
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }
        File branch = new File(GITLET, branchName);
        if (!branch.exists()) { // failure case
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }

        /** get commit of latest split point */
        Commit curr = getHEAD();
        Commit given = Utils.readObject(Utils.join(committingArea, Utils.readContentsAsString(Utils.join(GITLET, branchName))), Commit.class);
        Commit split = getSplitPoint(curr, given);

        if(given.getSha1Code().equals(split.getSha1Code())){
            System.out.println("Given branch is an ancestor of the current branch.");
        }else if(curr.getSha1Code().equals(split.getSha1Code())){
            String[] command = {"checkout", branchName};
            checkout(command);
            System.out.println("Current branch fast-forwarded.");
        }else{
            /** put all file names in a set */
            Set<String> fileNameSet = new HashSet<>();
            if(curr.getFiles() != null){
                fileNameSet.addAll(curr.getFiles().keySet());
            }
            if(given.getFiles() != null){
                fileNameSet.addAll(given.getFiles().keySet());
            }
            if(split != null && split.getFiles() != null){
                fileNameSet.addAll(split.getFiles().keySet());
            }

            for(String filename: fileNameSet){
                /** case 1 112*/
                if((checkContain(split, filename) && checkContain(curr, filename) && checkContain(given, filename)) &&
                        sameVersion(split, curr, filename) && (!sameVersion(split, given, filename))){
                    checkout(checkoutCommand(given, filename));
                    addFileToStaged(addCommand(filename));
                    /** case 2 121*/
                }else if((checkContain(split, filename) && checkContain(curr, filename) && checkContain(given, filename)) &&
                        sameVersion(split, given, filename) && (!sameVersion(split, curr, filename))){
                    continue;
                    /** case 3 122*/
                }else if((checkContain(split, filename) && checkContain(curr, filename) && checkContain(given, filename)) &&
                        sameVersion(curr, given, filename) && (!sameVersion(split, curr, filename))){
                    continue;
                    /** case 4 1XX*/
                }else if((checkContain(split, filename) && !checkContain(curr, filename) && !checkContain(given, filename))){
                    continue;
                    /** case 5 X1X*/
                }else if((!checkContain(split, filename) && checkContain(curr, filename) && !checkContain(given, filename))){
                    continue;
                    /** case 6 XX1*/
                }else if((!checkContain(split, filename) && !checkContain(curr, filename) && checkContain(given, filename))){
                    if (Utils.join(CWD, filename).exists()) {
                        exitWithError("There is an untracked file in the way; delete it, or add and commit it first.");
                    }
                    checkout(checkoutCommand(given, filename));
                    addFileToStaged(addCommand(filename));
                    /** case 7 11X*/
                }else if((checkContain(split, filename) && checkContain(curr, filename) && !checkContain(given, filename)) &&
                        sameVersion(split, curr, filename)){
                    File path = Utils.join(CWD, filename);
                    path.delete();
                    String[] command = {"rm", filename};
                    rm(command);
                    /** case 8 1X1*/
                }else if((checkContain(split, filename) && !checkContain(curr, filename) && checkContain(given, filename)) &&
                        sameVersion(split, given, filename)){
                    continue;
                    /** case 9 X11*/
                }else if((!checkContain(split, filename) && checkContain(curr, filename) && checkContain(given, filename)) &&
                        sameVersion(curr, given, filename)) {
                    continue;
                    /** case 10 123*/
                }else if((checkContain(split, filename) && checkContain(curr, filename) && checkContain(given, filename)) &&
                        (!sameVersion(curr, given, filename) && !sameVersion(curr, split, filename) && !sameVersion(split, given, filename))) {
                    mergeConflict2(curr, given, filename);
                    addFileToStaged(addCommand(filename));
                    conflict = true;
                    /** case 11 12X*/
                }else if((checkContain(split, filename) && checkContain(curr, filename) && !checkContain(given, filename)) &&
                        !sameVersion(split, curr, filename)){
                    mergeConflict3(curr, given, filename);
                    addFileToStaged(addCommand(filename));
                    conflict = true;
                    /** case 12 1X2*/
                }else if((checkContain(split, filename) && !checkContain(curr, filename) && checkContain(given, filename)) &&
                        !sameVersion(split, given, filename)){
                    if (Utils.join(CWD, filename).exists()) {
                        exitWithError("There is an untracked file in the way; delete it, or add and commit it first.");
                    }
                    mergeConflict1(curr, given, filename);
                    addFileToStaged(addCommand(filename));
                    conflict = true;
                    /** case 13 X12*/
                }else if((!checkContain(split, filename) && checkContain(curr, filename) && checkContain(given, filename)) &&
                        !sameVersion(curr, given, filename)){
                    mergeConflict2(curr, given, filename);
                    addFileToStaged(addCommand(filename));
                    conflict = true;
                }
            }
            /** commit */
            String[] command = {"commit", "Merged " + branchName + " into " + Utils.readContentsAsString(HEAD) + "."};
            makeCommit(command);
            Commit head = getHEAD();
            head.setParent2(given.getSha1Code());
            Utils.writeObject(Utils.join(committingArea, head.getSha1Code()), head);
            if(conflict){
                System.out.println("Encountered a merge conflict.");
            }
        }
    }
    public static void mergeConflict1(Commit curr, Commit branch, String filename){
        String branchContent = new String(Utils.readContents(Utils.join(blobsArea, branch.getFiles().get(filename))));
        File path = Utils.join(CWD, filename);
        Utils.writeContents(path, "<<<<<<< HEAD"
                + System.lineSeparator()
                + "=======" + System.lineSeparator() + branchContent
                + ">>>>>>>" + System.lineSeparator());
    }

    public static void mergeConflict2(Commit curr, Commit branch, String filename){
        String currContent = new String(Utils.readContents(Utils.join(blobsArea, curr.getFiles().get(filename))));
        String branchContent = new String(Utils.readContents(Utils.join(blobsArea, branch.getFiles().get(filename))));
        File path = Utils.join(CWD, filename);
        Utils.writeContents(path, "<<<<<<< HEAD\n"
                + currContent + "=======\n" + branchContent
                + ">>>>>>>\n");
    }

    public static void mergeConflict3(Commit curr, Commit branch, String filename){
        String currContent = new String(Utils.readContents(Utils.join(blobsArea, curr.getFiles().get(filename))));
        File path = Utils.join(CWD, filename);
        Utils.writeContents(path, "<<<<<<< HEAD"
                + System.lineSeparator()
                + currContent + "=======" + System.lineSeparator()
                + ">>>>>>>\n");
    }

    public static String[] checkoutCommand(Commit commit, String filename){
        String sha1 = commit.getSha1Code();
        String[] command = {"checkout",sha1 , "--", filename };
        return command;
    }

    public static String[] addCommand(String file){
        String[] command = {"add", file};
        return command;
    }

    public static boolean checkContain(Commit thisCommit, String file){
        return (thisCommit.getFiles() != null && thisCommit.getFiles().containsKey(file));
    }

    public static boolean sameVersion(Commit commit1, Commit commit2, String file){
        return (commit1.getFiles().get(file).equals(commit2.getFiles().get(file)));
    }

    public static boolean inBranch(Commit curr, Commit branchHead){
        if(curr == null || branchHead == null){
            return false;
        }if(curr.getSha1Code().equals(branchHead.getSha1Code())){
            return true;
        } else if(branchHead.getParent() == null){
            return false;
        } else{
            boolean result = false;
            if(branchHead.getParent() != null) {
                Commit branchParent1 = Utils.readObject(Utils.join(committingArea, branchHead.getParent()), Commit.class);
                if(inBranch(curr, branchParent1)){
                    result = true;
                }
            }
            if(branchHead.getParent2() != null){
                Commit branchParent2 = Utils.readObject(Utils.join(committingArea, branchHead.getParent2()), Commit.class);
                if(inBranch(curr, branchParent2)){
                    result = true;
                }
            }
            return result;
        }
    }

    public static Commit getSplitPoint(Commit curr, Commit givenHead){
        ArrayDeque<String> splitDeque = new ArrayDeque<>();
        splitDeque.add(curr.getSha1Code());
        while(!splitDeque.isEmpty()){
            Commit pop = Utils.readObject(Utils.join(committingArea, splitDeque.poll()), Commit.class);
            if(inBranch(pop, givenHead)){
                return pop;
            } else{
                if(pop.getParent() != null){
                    splitDeque.add(pop.getParent());
                }
                if(pop.getParent2() != null){
                    splitDeque.add(pop.getParent2());
                }
            }
        }
        return null;
    }


    /** helper method to get the commit stored in HEAD */
    public static Commit getHEAD(){
        File currHead = Utils.join(GITLET, Utils.readContentsAsString(HEAD));
        return Utils.readObject(Utils.join(committingArea, Utils.readContentsAsString(currHead)), Commit.class);
    }

    public static void exitWithError(String message) {
        if (message != null && !message.equals("")) {
            System.out.println(message);
        }
        System.exit(0);
    }

    public static HashSet<String> getAllParents(HashSet<String> parentSet, Commit branchHead){
        parentSet.add(branchHead.getSha1Code());
        if(branchHead.getParent() != null){
            Commit parent1 = Utils.readObject(Utils.join(committingArea, branchHead.getParent()), Commit.class);
            getAllParents(parentSet, parent1);
        }
        if(branchHead.getParent2() != null){
            Commit parent2 = Utils.readObject(Utils.join(committingArea, branchHead.getParent2()), Commit.class);
            getAllParents(parentSet, parent2);
        }
        return parentSet;
    }

    public static boolean shortenIdCheck(String shortId, String longId){
        for(int i = 0; i < shortId.length(); i++){
            if(shortId.charAt(i) != longId.charAt(i)){
                return false;
            }
        }
        return true;
    }
}
