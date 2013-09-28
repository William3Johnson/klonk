package org.tmotte.klonk.controller;
import java.awt.Component;
import java.awt.Container;
import java.awt.Image;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;
import javax.swing.SwingWorker;
import org.tmotte.common.swang.Fail;
import org.tmotte.klonk.config.FontOptions;
import org.tmotte.klonk.config.KPersist;
import org.tmotte.klonk.config.LineDelimiterOptions;
import org.tmotte.klonk.config.TabAndIndentOptions;
import org.tmotte.klonk.config.msg.Doer;
import org.tmotte.klonk.config.msg.Editors;
import org.tmotte.klonk.config.msg.Getter;
import org.tmotte.klonk.config.msg.Setter;
import org.tmotte.klonk.config.msg.StatusUpdate;
import org.tmotte.klonk.controller.Recents;
import org.tmotte.klonk.Editor;
import org.tmotte.klonk.EditorListener;
import org.tmotte.klonk.edit.UndoEvent;
import org.tmotte.klonk.edit.UndoListener;
import org.tmotte.klonk.windows.MainLayout; 
import org.tmotte.klonk.windows.popup.LineDelimiterListener;
import org.tmotte.klonk.windows.popup.Popups; 
import org.tmotte.klonk.windows.popup.YesNoCancelAnswer;

/** Refer to org.tmotte.klonk.config.Boot for application startup */

public class CtrlMain implements EditorListener {


  /////////////////////
  //                 //
  // INSTANCE STUFF: //
  //                 //
  /////////////////////

  
  //Main GUI components:
  private MainLayout layout;
  private Popups popups;
  private boolean anyUnsaved=false;
  private boolean thisUnsaved=false;
  private StatusUpdate statusBar;
  
  //Configuration stuff:
  private KPersist persist;
  private Fail fail;
  private Doer lockRemover, editorSwitchedListener;
  private Recents recents;

  //Editors list:
  private LinkedList<Editor> editors=new LinkedList<>();
  private Editors editorMgr=new Editors(){
    public Editor getFirst() {return editors.getFirst();}
    public Iterable<Editor> forEach() {return editors;}
    public int size() {return editors.size();}
  };

  //Constructor:
  public CtrlMain(Fail fail, final KPersist persist, Doer lockRemover) {
    this.fail=fail;
    this.lockRemover=lockRemover;
    this.persist=persist;
    recents=new Recents(persist); 
  }
  
  /////////////////////////////////
  //DI initialization functions: //
  /////////////////////////////////
  
  public void setLayout(MainLayout layout, StatusUpdate statusBar) {
    this.layout=layout;
    this.statusBar=statusBar;
  }
  public void setPopups(Popups p) {
    this.popups=p;
  }
  public void setListeners(
      Doer editorSwitchListener,
      Setter<List<String>> recentFileListener,
      Setter<List<String>> recentDirListener
    ) {  
    this.editorSwitchedListener=editorSwitchListener;
    this.recents.setFileListener(recentFileListener);
    this.recents.setDirListener(recentDirListener);
  }
  
  public Editors getEditors() {
    return editorMgr;
  }
  public Setter<List<String>> getFileReceiver() {
    return new Setter<List<String>>(){
      public @Override void set(List<String> files) 
        {doLoadAsync(files);}
    };
  }
  public Getter<String> getCurrFileNameGetter() {
    return new Getter<String>() {
      public @Override String get() 
        {return getCurrentFileName();}
    };
  }
  public Doer getAppCloseListener() {
    return new Doer() {
      //This is the application close listener:
      public @Override void doIt() 
        {tryExitSystem();}
    };
  }
  public LineDelimiterListener getLineDelimiterListener() {
    return new LineDelimiterListener() {
      public void setDefault(String defaultDelim) {
        persist.setDefaultLineDelimiter(defaultDelim);
        persist.save();
      }
      public void setThis(String lineB) {
        Editor e=editorMgr.getFirst();
        e.setLineBreaker(lineB);
        if (e.getFile()!=null && !e.hasUnsavedChanges())
          fileSave(false);
      }
    };  
  }
  
  /////////////////////////
  // STARTUP & SHUTDOWN: //
  /////////////////////////
  
  
  public void begin(final String[] args) {
    newEditor();
    loadFiles(args);
  }

  public void tryExitSystem() {
    while (editorMgr.size()>0)
      if (!fileCloseLastFirst(true))
        return;
    if (!layout.isMaximized()) {
      persist.setWindowBounds(layout.getMainWindowBounds());
      persist.setWindowMaximized(false);
    }
    else
      persist.setWindowMaximized(true);
    persist.save();
    lockRemover.doIt();
    layout.dispose();
    System.exit(0);
  }
 
  //////////////////////
  //                  //
  //   MENU EVENTS:   //
  //                  //
  //////////////////////


  ////////////////
  // FILE MENU: //
  ////////////////

  public void doFileOpenDialog() {
    File file=recents.hasDirs()
      ?popups.showFileDialogForDir(false, new File(recents.getFirstDir()))
      :popups.showFileDialog(false);
    if (file!=null)
      loadFile(file);
  }
  public void doNew() {
    newEditor();
  }
  public boolean doSave() {
    return doSave(false);
  }
  public boolean doSave(boolean forceNewFile) {
    return fileSave(forceNewFile);
  }
  public void doFileClose() {
    fileClose(false);
  }
  public void doFileCloseOthers() {
    while (editorMgr.size()>1)
      if (!fileCloseLastFirst(false))
        return;
  }
  public void doFileCloseAll() {
    //Close all but one, asking to save as necessary:
    while (editorMgr.size()>1)
      if (!fileCloseLastFirst(false))
        return;
    //If very last one is just an untitled, ignore and return.
    //Note that since we automatically create an untitled when
    //we close the last one, we have to be careful about an 
    //endless loop:
    if (!editorMgr.getFirst().isUsed())
      return;
    fileClose(false);
  }
  public void doLoadFile(String file) {
    loadFile(new File(file));
  }

 
  // FILE OPEN FROM/SAVE TO: //
  public void doOpenFromDocDir() {
    File file;
    if ((file=popups.showFileDialogForDir(false, editorMgr.getFirst().getFile().getParentFile()))!=null)
      loadFile(file);
  }
  public void doOpenFrom(String dir) {
    File file;
    if ((
      file=popups.showFileDialogForDir(false, new File(dir))
      )!=null)
      loadFile(file);
  }
  public void doSaveToDocDir() {
    File file;
    Editor ed=editorMgr.getFirst();
    if ((file=showFileSaveDialog(null, ed.getFile().getParentFile()))!=null)
      fileSave(ed, file, true);
  }
  public void doSaveTo(String dir) {
    File file;
    if ((
      file=showFileSaveDialog(null, new File(dir)) 
      )!=null)
      fileSave(editorMgr.getFirst(), file, true);
  }
  public void doFileExit() {
    tryExitSystem();
  }
  
  //////////////////
  // SWITCH MENU: //
  //////////////////

  public void doSendFrontToBack(){
    Editor e=editors.removeFirst();
    editors.addLast(e);
    setEditor(editors.getFirst());
  }
  public void doSendBackToFront(){
    Editor e=editors.removeLast();
    editors.addFirst(e);
    setEditor(e);
  }
  public void doSwitch(Editor editor) {
    editorSwitch(editor);
  }

  
  ////////////////////
  // EDITOR EVENTS: //
  ////////////////////

  public void doCapsLock(boolean state) {
    layout.showCapsLock(state);
  }
  public void doCaretMoved(Editor editor, int dotPos) {
    editorCaretMoved(editor, dotPos);
  }
  public void doEditorChanged(Editor e) {
    stabilityChange(e);
  }
  public void fileDropped(File file) {
    loadFile(file);
  }
  public void closeEditor() {
    fileClose(false);
  }
  

  ///////////////////////
  //                   //
  //  PRIVATE METHODS: //
  //                   //
  ///////////////////////

  private String getCurrentFileName() {
    File file=editorMgr.getFirst().getFile();
    return file==null ?null :getFullPath(file);
  }


  //////////////////
  // CARET STATE: //
  //////////////////

  private void editorCaretMoved(Editor e, int caretPos) {
    showCaretPos(e, caretPos);
    layout.showNoStatus();
  }
  private void showCaretPos(Editor e) {
    showCaretPos(e, e.getCaretPos());
  }
  private void showCaretPos(Editor e, int caretPos) {
    int rowPos=e.getRow(caretPos);
    layout.showRowColumn(rowPos+1, caretPos-e.getRowOffset(rowPos));
  }
  
  ////////////////
  // FILE SAVE: //
  ////////////////

  private boolean fileSave(boolean forceNewFile) {
    Editor e=editorMgr.getFirst();
    
    //1. Get file:
    boolean newFile=true;
    File file=null, oldFile=e.getFile();
    if (forceNewFile || oldFile==null) {
      File dir=oldFile==null && recents.hasDirs()
        ?new File(recents.getFirstDir())
        :null;
      if ((file=showFileSaveDialog(oldFile, dir))==null){
        statusBar.showBad("File save cancelled");
        return false;
      }
    }
    else {
      file=oldFile;
      newFile=false;
    }

    //2. Save file:
    return fileSave(e, file, newFile);
  }
  private boolean fileSave(Editor e, File file, boolean newFile) {
    File oldFile=newFile ?e.getFile() :null;
    try {
      statusBar.show("Saving...");
      e.saveFile(file);
    } catch (Exception ex) {
      fail.fail(ex);
      statusBar.showBad("Save failed.");
      return false;
    }
    fileIsSaved(e, file, oldFile, newFile);
    persist.checkSave();
    return true;
  }
  private File showFileSaveDialog(File f, File dir) {
    File file;
    if (f!=null)
      file=popups.showFileDialog(true,  f);
    else
    if (dir!=null)
      file=popups.showFileDialogForDir(true, dir);
    else
      file=popups.showFileDialog(true);
    if (file==null)
      return null;
    for (Editor e: editorMgr.forEach()) 
      if (e.sameFile(file)){
        popups.alert("File is already open in another window; close it first: "+e.getTitle());
        return null;
      }
    //This is only needed if we are using JFileChooser. The AWT chooser will result in a question
    //courtesy of the OS: 
    if (file.exists()) {
      if (!popups.askYesNo("Replace file "+file.getAbsolutePath()+"?"))
        return null;
      else
        file.delete();
    }
    return file;
  }

  /////////////////
  // FILE CLOSE: //
  /////////////////

  private boolean fileCloseLastFirst(boolean forExit) {
    if (editorMgr.size()>1){
      //This forces the app to close the most recently accessed
      //files last, so that they are the most likely to appear in the reopen menu:
      Editor e=editors.removeLast();
      editors.addFirst(e);
      setEditor(e);
    }
    return fileClose(forExit);
  }

  private boolean fileClose(boolean forExit) {
    Editor e=editorMgr.getFirst();
    if (e.hasUnsavedChanges()) {
      YesNoCancelAnswer result=popups.askYesNoCancel("Save changes to: "+e.getTitle()+"?");
      if (result.isYes()){
        if (!fileSave(false))
          return false;
      }
      else
      if (result.isCancel())
        return false;
      //"No" means we do nothing
    }
    if (e.getFile()!=null) 
      recents.recentFileClosed(e.getFile());
    editors.remove(0);
    statusBar.show("Closed: "+e.getTitle());
    if (editorMgr.size()>0)
      setEditor(editorMgr.getFirst());
    else
    if (!forExit)
      newEditor();
    return true;
  }

  ////////////////
  // FILE LOAD: //
  ////////////////

  private void doLoadAsync(final List<String> fileNames) {
    //Do nothing in the doInBackground() thread, since it is not
    //a GUI thread. Do it all in done(), which is sync'd to GUI events,
    //IE EventDispatch.
    new SwingWorker<String, Object>() {
      @Override public String doInBackground() {return "";}
      @Override protected void done(){
        try {
          loadFiles(fileNames);
        } catch (Exception e) {
          fail.fail(e);
        }
        fileNames.clear();
      }
    }.execute();
  }
  
  private void loadFiles(String[] args) {
    if (args!=null)
      for (String s: args) {
        if (s==null) continue;
        s=s.trim();
        if (s.equals("")) continue;
        loadFile(s);
      }
  }
  private void loadFiles(List<String> fileNames) {
    for (String s: fileNames) loadFile(s);
  }
  private void loadFile(String fileName) {
    loadFile(new File(fileName));
  }
  /** Makes sure file isn't already loaded, and finds an editor to load it into: **/
  private void loadFile(File file) {
    if (!file.exists()){
      popups.alert("No such file: "+file);
      return;
    }
    for (Editor ed: editorMgr.forEach()) 
      if (ed.sameFile(file)){
        editorSwitch(ed);
        popups.alert("File is already open: "+ed.getTitle());
        return;
      }
    try {
      Editor toUse=null;
      for (Editor e:editorMgr.forEach()) 
        if (!e.isUsed()) {
          toUse=e;
          editorSwitch(e);
          break;
        }
      if (toUse==null)
        toUse=newEditor();
      loadFile(toUse, file);
    } catch (Exception e) {
      fail.fail(e);
    }
  }
  private boolean loadFile(Editor e, File file) {
    try {
      statusBar.show("Loading: "+file+"...");
      e.loadFile(file, persist.getDefaultLineDelimiter());
    } catch (Exception ex) {
      fail.fail(ex);
      statusBar.showBad("Load failed");
      return false;
    }
    fileIsLoaded(e, file);
    return true;
  }


  ////////////////////////
  // EDITOR MANAGEMENT: //
  ////////////////////////

  private void editorSwitch(Editor editor) {
    editors.remove(editors.indexOf(editor));
    editors.add(0, editor);
    setEditor(editor);
  }
  private Editor newEditor(){
    Editor e=new Editor(
      this, fail, myUndoListener, 
      persist.getDefaultLineDelimiter(), persist.getWordWrap()
    ); 
    e.setFastUndos(persist.getFastUndos());
    e.setTitle(getUnusedTitle());
    TabAndIndentOptions taio=persist.getTabAndIndentOptions();
    e.setTabAndIndentOptions(taio);
    e.setTabsOrSpaces(taio.indentionModeDefault);
    e.setFont(persist.getFontAndColors());

    editors.add(0, e);
    setEditor(e); 
    return e;
  }
  private void setEditor(Editor e) {
    layout.setEditor(e.getContainer(), e.getTitle());
    editorSwitched(e);
    e.requestFocus();
  }

  private String getUnusedTitle() {
    String name="Untitled";
    int incr=0;
    while (isUsedTitle(name))
      name="Untitled "+(++incr);
    return name;
  }
  private boolean isUsedTitle(String name) {
    for (Editor ed:editorMgr.forEach()) 
      if (ed.getTitle().equals(name))
        return true;
    return false;
  }



  //////////////////////////////
  // ULTRA COMPLICATED STATE: //
  //////////////////////////////

  private UndoListener myUndoListener=new UndoListener() {
    public void happened(UndoEvent ue) {
      if (ue.isNoMoreUndos)
        statusBar.showBad("No more undos.");
      else
      if (ue.isNoMoreRedos)
        statusBar.showBad("No more redos.");
      else
      if (ue.isUndoSaveStable)
        stabilityChange(editorMgr.getFirst());
    }
  };

  private void fileIsSaved(Editor e, File file, File oldFile, boolean newFile) {
    if (newFile){
      editorSwitched(e);
      recents.recentFileSavedNew(file, oldFile);
    }
    else
      stabilityChange(e);
    statusBar.show(newFile ?"File saved: "+file :"File saved");
  }
  private void fileIsLoaded(Editor e, File file) {
    recents.recentFileLoaded(file);
    editorSwitched(e);
    statusBar.show("File loaded: "+file);
  }
  
  private void stabilityChange(Editor editor) {
    boolean b=editor.hasUnsavedChanges();
    if (thisUnsaved!=b){
      thisUnsaved=b;
      showLights();
    }
  }
  private void showLights() {
    //This adjusts the red/blue lights:
    layout.showChangeThis(thisUnsaved);
    if (thisUnsaved) {
      if (!anyUnsaved)
        layout.showChangeAny(anyUnsaved=true);
      return;
    }
    else 
    if (anyUnsaved) 
      for (Editor ed: editors)
        if (ed.hasUnsavedChanges())
          return;
    layout.showChangeAny(anyUnsaved=false);
  }
  /** Invoked whenever we switch to a different editor, 
      or current editor is used to open a file
      or current editor is saved to a new file. */
  private void editorSwitched(Editor e) {
    thisUnsaved=e.hasUnsavedChanges();
    showLights();
    layout.showTitle(e.getTitle());
    showCaretPos(e);
    editorSwitchedListener.doIt();
  }


  ////////////////
  // UTILITIES: //
  ////////////////

  private String getFullPath(File file) {
    try {
      return file.getCanonicalPath();
    } catch (Exception e) {
      fail.fail(e);
      return null;
    }
  }
}
