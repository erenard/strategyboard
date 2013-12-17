#{if session.username && controllers.Secure.Security.invoke("isConnected")}
    #{doBody /}
#{/if}