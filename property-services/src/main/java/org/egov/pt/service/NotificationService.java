package org.egov.pt.service;

import static org.egov.pt.util.PTConstants.MT_NO_WORKFLOW;
import static org.egov.pt.util.PTConstants.NOTIFICATION_UPDATED_CREATED_REPLACE;
import static org.egov.pt.util.PTConstants.NOTIFICATION_UPDATE_CREATE_REPLACE;
import static org.egov.pt.util.PTConstants.UPDATE_NO_WORKFLOW;
import static org.egov.pt.util.PTConstants.WF_MT_STATUS_APPROVED_CODE;
import static org.egov.pt.util.PTConstants.WF_MT_STATUS_CHANGE_CODE;
import static org.egov.pt.util.PTConstants.WF_MT_STATUS_OPEN_CODE;
import static org.egov.pt.util.PTConstants.WF_MT_STATUS_PAID_CODE;
import static org.egov.pt.util.PTConstants.WF_MT_STATUS_PAYMENT_PENDING_CODE;
import static org.egov.pt.util.PTConstants.WF_NO_WORKFLOW;
import static org.egov.pt.util.PTConstants.WF_STATUS_APPROVED;
import static org.egov.pt.util.PTConstants.WF_STATUS_OPEN;
import static org.egov.pt.util.PTConstants.WF_STATUS_PAID;
import static org.egov.pt.util.PTConstants.WF_STATUS_PAYMENT_PENDING;
import static org.egov.pt.util.PTConstants.WF_UPDATE_STATUS_APPROVED_CODE;
import static org.egov.pt.util.PTConstants.WF_UPDATE_STATUS_CHANGE_CODE;
import static org.egov.pt.util.PTConstants.WF_UPDATE_STATUS_OPEN_CODE;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.egov.pt.config.PropertyConfiguration;
import org.egov.pt.models.AuditDetails;
import org.egov.pt.models.Property;
import org.egov.pt.models.workflow.ProcessInstance;
import org.egov.pt.util.NotificationUtil;
import org.egov.pt.util.PTConstants;
import org.egov.pt.web.contracts.PropertyRequest;
import org.egov.pt.web.contracts.SMSRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private NotificationUtil notifUtil;
    
    @Autowired
    private PropertyConfiguration configs;

    @Value("${notification.url}")
    private String notificationURL;
    
    public void sendNotificationForMutation(PropertyRequest propertyRequest) {
    	
    	
    	Property property = propertyRequest.getProperty();
    	
    	String CompleteMsgs = notifUtil.getLocalizationMessages(property.getTenantId(), propertyRequest.getRequestInfo());
    	
    	String state = property.getWorkflow().getState() != null ? property.getWorkflow().getState().getState() : WF_NO_WORKFLOW;
    	String msg = null;
    	
    	switch (state) {
    	
		case WF_NO_WORKFLOW :
			
			msg = notifUtil.getMessageTemplate(MT_NO_WORKFLOW, CompleteMsgs);
			
			break;	
    	
		case WF_STATUS_OPEN :
			msg = notifUtil.getMessageTemplate(WF_MT_STATUS_OPEN_CODE, CompleteMsgs);
			
			break;	
			
		case WF_STATUS_APPROVED :
			msg = notifUtil.getMessageTemplate(WF_MT_STATUS_APPROVED_CODE, CompleteMsgs);
			msg.replace(PTConstants.NOTIFICATION_CERT_LINK, "");
			break;
			
		case WF_STATUS_PAYMENT_PENDING :
			msg = notifUtil.getMessageTemplate(WF_MT_STATUS_PAYMENT_PENDING_CODE, CompleteMsgs);
			msg.replace(PTConstants.NOTIFICATION_PAY_LINK, "");
			break;
			
		case WF_STATUS_PAID:
			break;
			
		default:
			msg = notifUtil.getMessageTemplate(WF_MT_STATUS_CHANGE_CODE, CompleteMsgs);
			break;
		}
    	
    	// Ignoring paid status, since it's wired from payment consumer directly
    	
		if (msg != null) {
			replaceCommonValues(property, msg);
			prepareMsgAndSend(property, msg);
		}
	}

    public void sendNotificationForMtPayment(PropertyRequest propertyRequest, BigDecimal Amount) {
    	
    	Property property = propertyRequest.getProperty();
    	String state = property.getWorkflow().getState().getState();
    	
    	String CompleteMsgs = notifUtil.getLocalizationMessages(property.getTenantId(), propertyRequest.getRequestInfo());
    	if(state.equalsIgnoreCase(WF_STATUS_PAID)) {
    		
    		String msg = notifUtil.getMessageTemplate(WF_MT_STATUS_PAID_CODE, CompleteMsgs);
    		replaceCommonValues(property, msg);
    		msg.replace(PTConstants.NOTIFICATION_RCPT_LINK, "");
    		msg.replace(PTConstants.NOTIFICATION_AMOUNT, Amount.toPlainString());
    		prepareMsgAndSend(property, msg);
    	}
    }
    
    public void sendNotificationForUpdate (PropertyRequest propertyRequest) {
    	
    	
    	Property property = propertyRequest.getProperty();
    	
    	String CompleteMsgs = notifUtil.getLocalizationMessages(property.getTenantId(), propertyRequest.getRequestInfo());
    	AuditDetails audit = property.getAuditDetails();
    	String msg = null;
    	String state = null;
    	Boolean isCreate = null;
    	ProcessInstance wf = property.getWorkflow();
    	
		if (wf != null) {

			isCreate = wf.getBusinessService().equalsIgnoreCase(configs.getCreatePTWfName());
			state = property.getWorkflow().getState().getState();
		} else {

			isCreate = audit.getCreatedTime().compareTo(audit.getLastModifiedTime()) == 0;
			state = WF_NO_WORKFLOW;
		}
    	

    	switch (state) {
    	
		case WF_NO_WORKFLOW :
			
			msg = notifUtil.getMessageTemplate(UPDATE_NO_WORKFLOW, CompleteMsgs);
			msg = msg.replace(NOTIFICATION_UPDATED_CREATED_REPLACE, isCreate ? PTConstants.CREATED_STRING : PTConstants.UPDATED_STRING);
			break;	
    	
		case WF_STATUS_OPEN :
			msg = notifUtil.getMessageTemplate(WF_UPDATE_STATUS_OPEN_CODE, CompleteMsgs);
			msg = msg.replace(NOTIFICATION_UPDATE_CREATE_REPLACE, isCreate ? PTConstants.CREATE_STRING : PTConstants.UPDATE_STRING);
			break;	
			
		case WF_STATUS_APPROVED :
			msg = notifUtil.getMessageTemplate(WF_UPDATE_STATUS_APPROVED_CODE, CompleteMsgs);
			msg = msg.replace(NOTIFICATION_UPDATED_CREATED_REPLACE, isCreate ? PTConstants.CREATED_STRING : PTConstants.UPDATED_STRING);
			break;
			
		default:
			msg = notifUtil.getMessageTemplate(WF_UPDATE_STATUS_CHANGE_CODE, CompleteMsgs);
			msg = msg.replace(NOTIFICATION_UPDATE_CREATE_REPLACE, isCreate ? PTConstants.CREATE_STRING : PTConstants.UPDATE_STRING);
			break;
		}
    	
    	msg = replaceCommonValues(property, msg);
    	prepareMsgAndSend(property, msg);
	}
    
	private String replaceCommonValues(Property property, String msg) {
		
		msg = msg.replace(PTConstants.NOTIFICATION_PROPERTYID, property.getPropertyId());
		msg = msg.replace(PTConstants.NOTIFICATION_STATUS, property.getStatus().toString());
		msg = msg.replace(PTConstants.NOTIFICATION_APPID, property.getAcknowldgementNumber());
		msg = msg.replace(PTConstants.NOTIFICATION_OWNERNAME, property.getOwners().get(0).getName());
		return msg.replace(PTConstants.NOTIFICATION_SEARCH_LINK, "");
	}
	
	private void prepareMsgAndSend (Property property, String msg) {
		
		 List<SMSRequest> smsRequests = new LinkedList<>();
		
        Map<String,String > mobileNumberToOwner = new HashMap<>();

        property.getOwners().forEach(owner -> {
            if(owner.getMobileNumber()!=null)
                mobileNumberToOwner.put(owner.getMobileNumber(),owner.getName());
        });
        smsRequests.addAll(notifUtil.createSMSRequest(msg,mobileNumberToOwner));
        
        notifUtil.sendSMS(smsRequests);
	}
      
}