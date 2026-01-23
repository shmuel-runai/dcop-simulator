package utils.protocols.mpc.copy;

import java.util.HashMap;
import java.util.Map;
import utils.protocols.core.IProtocolMessage;


/**
 * Message broadcast by initiator to request all participants to copy a share.
 */
public class CopyShareMessage implements IProtocolMessage {
    
    private final String protocolId;
    private final int senderId;
    private final String srcSecretId;
    private final String dstSecretId;
    
    public CopyShareMessage(String protocolId, int senderId, String srcSecretId, String dstSecretId) {
        this.protocolId = protocolId;
        this.senderId = senderId;
        this.srcSecretId = srcSecretId;
        this.dstSecretId = dstSecretId;
    }
    
    @Override
    public String getProtocolId() {
        return protocolId;
    }
    
    @Override
    public String getProtocolType() {
        return SecureCopyShareProtocol.PROTOCOL_TYPE;
    }
    
    public int getSenderId() {
        return senderId;
    }
    
    public String getSrcSecretId() {
        return srcSecretId;
    }
    
    public String getDstSecretId() {
        return dstSecretId;
    }
    
    @Override
    public Map<String, Object> extractParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("srcSecretId", srcSecretId);
        params.put("dstSecretId", dstSecretId);
        params.put("initiatorId", Integer.valueOf(senderId));
        return params;
    }
}

