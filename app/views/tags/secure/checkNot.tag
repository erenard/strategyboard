#{if session.username && controllers.Secure.Security.invoke("check", _arg)}
#{/if}
#{else}
    #{doBody /}
#{/else}