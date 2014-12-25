package org.tmotte.klonk.ssh;
import com.jcraft.jsch.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import org.tmotte.klonk.config.msg.Setter;

public class SSHExec {
  
  private SSH ssh;
  private Setter<String> logger, alertHandler;
  SSHExec(SSH ssh, Setter<String> logger, Setter<String> alertHandler) {
    this.ssh=ssh;
    this.logger=logger;
  }
  
  SSHExecResult exec(String command, boolean alertFail) {
    StringBuilder out=new StringBuilder();
    ByteArrayOutputStream err=new ByteArrayOutputStream(512);
    int result=exec(command, out, err);
    if (err.size()>0 || result!=0)
      try {
        out.append(err.toString("utf-8"));
        result=1;
        String bad=out.toString();
        if (alertFail)
          alertHandler.set(bad);
        mylog("Fail: "+bad);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    return new SSHExecResult(result==0, out.toString());
  }
  int exec(String command, Appendable out, Appendable err) throws Exception {
    ByteArrayOutputStream baos=new ByteArrayOutputStream(512);
    int result=exec(command, out, baos);
    try {
      err.append(baos.toString("utf-8"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return result;
  }
  
  /** 
   * @param sshErr DO NOT PASS SYSTEM.ERR/OUT TO THIS, IT WILL GET CHEWED TO PIECES (i.e. it will be closed by 
   *        the jsch library).
   * @return The output (typically 0,1,2) of the unix command, or -1 if we could not get a connection.
   */
  int exec(String command, Appendable out, OutputStream sshErr) {
    mylog(command);
    try {
      Session session=ssh.getSession();
      if (session==null)
        return -1;
      ChannelExec channel=(ChannelExec)session.openChannel("exec");
      channel.setCommand(command);      
      channel.setErrStream(sshErr);
      channel.setOutputStream(null);
      InputStream in=channel.getInputStream();
      channel.connect();
      byte[] tmp=new byte[1024];
      try {
        //This loop will spin rather heavily. 
        while (!channel.isClosed()){
          while (in.available()>0){
            int i=in.read(tmp, 0, 1024);
            if (i<0) break;
            out.append(new String(tmp, 0, i));
          }
          //Doing this will cause output to be lost. I do not know why.
          //try {Thread.sleep(10);} catch (Exception e) {e.printStackTrace();}
        }
        int result=channel.getExitStatus();
        sshErr.flush();
        return result;
      } finally {
        channel.disconnect();  
        //Doing this will cause output to be lost which doesn't make sense. Let's blame the compiler:
        //in.close(); 
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute: "+command, e);
    }
  }

  private void mylog(String s) {
    logger.set("SSHExec: "+s);
  }

  
}
