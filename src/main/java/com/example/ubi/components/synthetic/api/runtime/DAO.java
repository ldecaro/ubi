package com.example.ubi.components.synthetic.api.runtime;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import com.example.ubi.Constants;

public class DAO{

    private Connection conn = null;

    static{
        try{
        Class.forName("software.aws.rds.jdbc.mysql.Driver");
        }catch(ClassNotFoundException ne){
            System.out.println("Could not find driver class in the classpath. Msg: "+ne);
        }
    }

    public DAO(){}

    public Integer keepalive(String region){

        Integer key = 0;
        if(this.conn == null){
            this.conn = getConnection();            
        }
        if(this.conn == null ){
            System.out.println("Could not create the Connection with the database. Check credentials, network, firewall, dns");
            return key;
        }
        synchronized(this.conn){
            if( this.conn == null ) return key;
            try(PreparedStatement stmt = this.conn.prepareStatement("INSERT INTO ubi.keepalive(comment)VALUES(?)", Statement.RETURN_GENERATED_KEYS)){
                
                stmt.setString(1, region);
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                if(rs.next()){
                    key = rs.getInt(1);
                    rs.close();
                }
            }catch(SQLException sqle){
                System.out.println("Could not create statement. Msg:"+sqle.getMessage());
                sqle.printStackTrace();
                //in case we have communication link failure we can try recreating the connection so that the next request doesn't fail.
                this.conn = getConnection();
            }
        }
        return key;
    }

    public Integer demo(String region){

        Integer key = 0;
        if(this.conn == null){
            this.conn = getConnection();            
        }
        if(this.conn == null ){
            System.out.println("Could not create the Connection with the database. Check credentials, network, firewall, dns");
            return key;
        }
        synchronized(this.conn){
            if( this.conn == null ) return key;
            try(PreparedStatement stmt = this.conn.prepareStatement("INSERT INTO ubi.demo(comment)VALUES(?)", Statement.RETURN_GENERATED_KEYS)){
                
                stmt.setString(1, region);
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                if(rs.next()){
                    key = rs.getInt(1);
                    rs.close();
                }
            }catch(SQLException sqle){
                System.out.println("Could not create statement. Msg:"+sqle.getMessage());
                sqle.printStackTrace();
                //in case we have communication link failure we can try recreating the connection so that the next request doesn't fail.
                this.conn = getConnection();
            }
        }
        return key;
    }    

    private Connection getConnection() {

        Connection conn = null;
        try{
            Properties props = new Properties();
            props.put("user", "clusteradmin");
            props.put("password", "welcome1");
            props.put("enableClusterAwareFailover", "false");

            // conn = DriverManager.getConnection("jdbc:mysql:aws://docker.for.mac.host.internal:3307/ubi", props);
            conn = DriverManager.getConnection("jdbc:mysql:aws://db."+Constants.DOMAIN+":3306/ubi", props);
            System.out.println("Connection Created. Is conn valid? "+conn.isValid(10000));
        }catch(SQLException sqle){
            System.out.println("Error connecting to the database using the jdbc url you provided. Msg: "+sqle);
            sqle.printStackTrace();
        }
        return conn;
    }    

    public void finalize(){
        try{
            if(this.conn!= null)
                this.conn.close();
        }catch(Exception e){
            System.out.println("Could not close SQL connection. Msg"+e.getMessage());
        }
    }
}