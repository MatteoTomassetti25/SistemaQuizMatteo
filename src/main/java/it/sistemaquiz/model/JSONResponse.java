package it.sistemaquiz.model;

public class JSONResponse {

    private String status;
    private String messaggio;
    
    public JSONResponse() {
    	
    }

    public JSONResponse(String status, String messaggio) {
        this.status = status;
        this.messaggio = messaggio;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessaggio() {
        return messaggio;
    }

    public void setMessaggio(String messaggio) {
        this.messaggio = messaggio;
    }
}