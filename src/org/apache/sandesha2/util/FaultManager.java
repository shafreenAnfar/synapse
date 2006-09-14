/*
 * Copyright  1999-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.sandesha2.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.context.ServiceGroupContext;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisOperationFactory;
import org.apache.axis2.util.Utils;
import org.apache.axis2.wsdl.WSDLConstants.WSDL20_2004Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sandesha2.FaultData;
import org.apache.sandesha2.RMMsgContext;
import org.apache.sandesha2.Sandesha2Constants;
import org.apache.sandesha2.SandeshaException;
import org.apache.sandesha2.i18n.SandeshaMessageHelper;
import org.apache.sandesha2.i18n.SandeshaMessageKeys;
import org.apache.sandesha2.storage.StorageManager;
import org.apache.sandesha2.storage.beanmanagers.CreateSeqBeanMgr;
import org.apache.sandesha2.storage.beanmanagers.NextMsgBeanMgr;
import org.apache.sandesha2.storage.beanmanagers.SequencePropertyBeanMgr;
import org.apache.sandesha2.storage.beans.CreateSeqBean;
import org.apache.sandesha2.storage.beans.NextMsgBean;
import org.apache.sandesha2.storage.beans.SequencePropertyBean;
import org.apache.sandesha2.wsrm.AcknowledgementRange;
import org.apache.sandesha2.wsrm.CreateSequence;
import org.apache.sandesha2.wsrm.Sequence;
import org.apache.sandesha2.wsrm.SequenceAcknowledgement;

/**
 * Has logic to check for possible RM related faults and create it.
 */

public class FaultManager {

	private static final Log log = LogFactory.getLog(FaultManager.class);

	public FaultManager() {
	}

	/**
	 * Check weather the CreateSequence should be refused and generate the fault
	 * if it should.
	 * 
	 * @param messageContext
	 * @return
	 * @throws SandeshaException
	 */
	public RMMsgContext checkForCreateSequenceRefused(MessageContext createSequenceMessage,
			StorageManager storageManager) throws SandeshaException {

		if (log.isDebugEnabled())
			log.debug("Enter: FaultManager::checkForCreateSequenceRefused");

		RMMsgContext createSequenceRMMsg = MsgInitializer.initializeMessage(createSequenceMessage);

		CreateSequence createSequence = (CreateSequence) createSequenceRMMsg
				.getMessagePart(Sandesha2Constants.MessageParts.CREATE_SEQ);
		if (createSequence == null)
			throw new SandeshaException(SandeshaMessageHelper.getMessage(SandeshaMessageKeys.noCreateSeqParts));

		ConfigurationContext context = createSequenceMessage.getConfigurationContext();
		if (storageManager == null)
			throw new SandeshaException(SandeshaMessageHelper.getMessage(SandeshaMessageKeys.cannotGetStorageManager));

		boolean refuseSequence = false;
		String reason = "";

		if (refuseSequence) {
			FaultData data = new FaultData();
			data.setType(Sandesha2Constants.SOAPFaults.FaultType.CREATE_SEQUENCE_REFUSED);
			int SOAPVersion = SandeshaUtil.getSOAPVersion(createSequenceRMMsg.getSOAPEnvelope());
			if (SOAPVersion == Sandesha2Constants.SOAPVersion.v1_1)
				data.setCode(SOAP11Constants.FAULT_CODE_SENDER);
			else
				data.setCode(SOAP12Constants.FAULT_CODE_SENDER);

			data.setSubcode(Sandesha2Constants.SOAPFaults.Subcodes.CREATE_SEQUENCE_REFUSED);
			data.setReason(reason);
			if (log.isDebugEnabled())
				log.debug("Exit: FaultManager::checkForCreateSequenceRefused, refused sequence");
			return getFault(createSequenceRMMsg, data, createSequenceRMMsg.getAddressingNamespaceValue(),
					storageManager);
		}

		if (log.isDebugEnabled())
			log.debug("Exit: FaultManager::checkForCreateSequenceRefused");
		return null;

	}

	/**
	 * Check weather the LastMessage number has been exceeded and generate the
	 * fault if it is.
	 * 
	 * @param msgCtx
	 * @return
	 */
	public RMMsgContext checkForLastMsgNumberExceeded(RMMsgContext applicationRMMessage, StorageManager storageManager)
			throws SandeshaException {
		if (log.isDebugEnabled())
			log.debug("Enter: FaultManager::checkForLastMsgNumberExceeded");
		Sequence sequence = (Sequence) applicationRMMessage.getMessagePart(Sandesha2Constants.MessageParts.SEQUENCE);
		long messageNumber = sequence.getMessageNumber().getMessageNumber();
		String sequenceID = sequence.getIdentifier().getIdentifier();

		ConfigurationContext configCtx = applicationRMMessage.getMessageContext().getConfigurationContext();
		SequencePropertyBeanMgr seqPropMgr = storageManager.getSequencePropertyBeanMgr();

		boolean lastMessageNumberExceeded = false;
		String reason = null;
		SequencePropertyBean lastMessageBean = seqPropMgr.retrieve(sequenceID,
				Sandesha2Constants.SequenceProperties.LAST_OUT_MESSAGE_NO);
		if (lastMessageBean != null) {
			long lastMessageNo = Long.parseLong(lastMessageBean.getValue());
			if (messageNumber > lastMessageNo) {
				lastMessageNumberExceeded = true;
				reason = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.msgNumberLargerThanLastMsg, Long
						.toString(messageNumber), Long.toString(lastMessageNo));
			}
		}

		if (lastMessageNumberExceeded) {
			FaultData faultData = new FaultData();
			faultData.setType(Sandesha2Constants.SOAPFaults.FaultType.LAST_MESSAGE_NO_EXCEEDED);
			int SOAPVersion = SandeshaUtil.getSOAPVersion(applicationRMMessage.getSOAPEnvelope());
			if (SOAPVersion == Sandesha2Constants.SOAPVersion.v1_1)
				faultData.setCode(SOAP11Constants.FAULT_CODE_SENDER);
			else
				faultData.setCode(SOAP12Constants.FAULT_CODE_SENDER);

			faultData.setSubcode(Sandesha2Constants.SOAPFaults.Subcodes.LAST_MESSAGE_NO_EXCEEDED);
			faultData.setReason(reason);
			if (log.isDebugEnabled())
				log.debug("Exit: FaultManager::checkForLastMsgNumberExceeded, lastMessageNumberExceeded");
			return getFault(applicationRMMessage, faultData, applicationRMMessage.getAddressingNamespaceValue(),
					storageManager);
		}

		if (log.isDebugEnabled())
			log.debug("Exit: FaultManager::checkForLastMsgNumberExceeded");
		return null;
	}

	public RMMsgContext checkForMessageNumberRoleover(MessageContext messageContext) {
		return null;
	}

	/**
	 * Check whether a Sequence message (a) belongs to a unknown sequence
	 * (generates an UnknownSequence fault) (b) message number exceeds a
	 * predifined limit ( genenrates a Message Number Rollover fault)
	 * 
	 * @param msgCtx
	 * @return
	 * @throws SandeshaException
	 */
	public RMMsgContext checkForUnknownSequence(RMMsgContext rmMessageContext, String sequenceID,
			StorageManager storageManager) throws SandeshaException {
		if (log.isDebugEnabled())
			log.debug("Enter: FaultManager::checkForUnknownSequence, " + sequenceID);

		MessageContext messageContext = rmMessageContext.getMessageContext();

		CreateSeqBeanMgr createSeqMgr = storageManager.getCreateSeqBeanMgr();
		int type = rmMessageContext.getMessageType();

		boolean validSequence = false;

		// Look for an outbound sequence
		CreateSeqBean createSeqFindBean = new CreateSeqBean();
		createSeqFindBean.setSequenceID(sequenceID);

		Collection coll = createSeqMgr.find(createSeqFindBean);
		if (!coll.isEmpty()) {
			validSequence = true;

		} else {
			// Look for an inbound sequence
			NextMsgBeanMgr mgr = storageManager.getNextMsgBeanMgr();

			coll = mgr.retrieveAll();
			Iterator it = coll.iterator();

			while (it.hasNext()) {
				NextMsgBean nextMsgBean = (NextMsgBean) it.next();
				String tempId = nextMsgBean.getSequenceID();
				if (tempId.equals(sequenceID)) {
					validSequence = true;
					break;
				}
			}
		}

		String rmNamespaceValue = rmMessageContext.getRMNamespaceValue();

		if (!validSequence) {

			if (log.isDebugEnabled())
				log.debug("Sequence not valid " + sequenceID);

			// Return an UnknownSequence error
			int SOAPVersion = SandeshaUtil.getSOAPVersion(messageContext.getEnvelope());

			FaultData data = new FaultData();
			if (SOAPVersion == Sandesha2Constants.SOAPVersion.v1_1)
				data.setCode(SOAP11Constants.FAULT_CODE_SENDER);
			else
				data.setCode(SOAP12Constants.FAULT_CODE_SENDER);

			data.setSubcode(Sandesha2Constants.SOAPFaults.Subcodes.UNKNOWN_SEQUENCE);

			SOAPFactory factory = SOAPAbstractFactory.getSOAPFactory(SOAPVersion);
			// Identifier identifier = new Identifier(factory,rmNamespaceValue);
			// identifier.setIndentifer(sequenceID);
			// OMElement identifierOMElem = identifier.getOMElement();

			OMElement identifierElement = factory.createOMElement(Sandesha2Constants.WSRM_COMMON.IDENTIFIER,
					rmNamespaceValue, Sandesha2Constants.WSRM_COMMON.NS_PREFIX_RM);
			data.setDetail(identifierElement);

			data.setReason(SandeshaMessageHelper.getMessage(SandeshaMessageKeys.noSequenceEstablished, sequenceID));

			if (log.isDebugEnabled())
				log.debug("Exit: FaultManager::checkForUnknownSequence");

			return getFault(rmMessageContext, data, rmMessageContext.getAddressingNamespaceValue(), storageManager);
		}

		if (log.isDebugEnabled())
			log.debug("Exit: FaultManager::checkForUnknownSequence");
		return null;
	}

	/**
	 * Check weather the Acknowledgement is invalid and generate a fault if it
	 * is.
	 * 
	 * @param msgCtx
	 * @return
	 * @throws SandeshaException
	 */
	public RMMsgContext checkForInvalidAcknowledgement(RMMsgContext ackRMMessageContext, StorageManager storageManager)
			throws SandeshaException {
		if (log.isDebugEnabled())
			log.debug("Enter: FaultManager::checkForInvalidAcknowledgement");

		// check lower<=upper
		// TODO acked for not-send message

		MessageContext ackMessageContext = ackRMMessageContext.getMessageContext();
		if (ackRMMessageContext.getMessageType() != Sandesha2Constants.MessageTypes.ACK) {
			if (log.isDebugEnabled())
				log.debug("Exit: FaultManager::checkForInvalidAcknowledgement, MessageType not an ACK");
			return null;
		}

		boolean invalidAck = false;
		String reason = null;
		
		Iterator sequenceAckIter = ackRMMessageContext.getMessageParts(
				Sandesha2Constants.MessageParts.SEQ_ACKNOWLEDGEMENT);
		
		while (sequenceAckIter.hasNext()) {
			SequenceAcknowledgement sequenceAcknowledgement = (SequenceAcknowledgement) sequenceAckIter.next();
			List sequenceAckList = sequenceAcknowledgement.getAcknowledgementRanges();
			Iterator it = sequenceAckList.iterator();

			while (it.hasNext()) {
				AcknowledgementRange acknowledgementRange = (AcknowledgementRange) it.next();
				long upper = acknowledgementRange.getUpperValue();
				long lower = acknowledgementRange.getLowerValue();

				if (lower > upper) {
					invalidAck = true;
					reason = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.ackInvalid, Long.toString(lower), Long
							.toString(upper));
				}
			}

			if (invalidAck) {
				FaultData data = new FaultData();
				int SOAPVersion = SandeshaUtil.getSOAPVersion(ackMessageContext.getEnvelope());
				if (SOAPVersion == Sandesha2Constants.SOAPVersion.v1_1)
					data.setCode(SOAP11Constants.FAULT_CODE_SENDER);
				else
					data.setCode(SOAP12Constants.FAULT_CODE_SENDER);

				data.setSubcode(Sandesha2Constants.SOAPFaults.Subcodes.INVALID_ACKNOWLEDGEMENT);
				data.setReason(reason);

				SOAPFactory factory = SOAPAbstractFactory.getSOAPFactory(SOAPVersion);
				OMElement dummyElement = factory.createOMElement("dummyElem", null);
				sequenceAcknowledgement.toOMElement(dummyElement);

				OMElement sequenceAckElement = dummyElement.getFirstChildWithName(new QName(
						Sandesha2Constants.WSRM_COMMON.SEQUENCE_ACK));
				data.setDetail(sequenceAckElement);

				if (log.isDebugEnabled())
					log.debug("Exit: FaultManager::checkForInvalidAcknowledgement, invalid ACK");
				return getFault(ackRMMessageContext, data, ackRMMessageContext.getAddressingNamespaceValue(),
						storageManager);
			}
		
		}

		if (log.isDebugEnabled())
			log.debug("Exit: FaultManager::checkForInvalidAcknowledgement");
		
		return null;
	}

	public RMMsgContext checkForSequenceClosed(RMMsgContext referenceRMMessage, String sequenceID,
			StorageManager storageManager) throws SandeshaException {
		if (log.isDebugEnabled())
			log.debug("Enter: FaultManager::checkForSequenceClosed, " + sequenceID);

		MessageContext referenceMessage = referenceRMMessage.getMessageContext();
		ConfigurationContext configCtx = referenceMessage.getConfigurationContext();

		SequencePropertyBeanMgr seqPropMgr = storageManager.getSequencePropertyBeanMgr();

		boolean sequenceClosed = false;
		String reason = null;
		SequencePropertyBean sequenceClosedBean = seqPropMgr.retrieve(sequenceID,
				Sandesha2Constants.SequenceProperties.SEQUENCE_CLOSED);
		if (sequenceClosedBean != null && Sandesha2Constants.VALUE_TRUE.equals(sequenceClosedBean.getValue())) {
			sequenceClosed = true;
			reason = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.cannotAcceptMsgAsSequenceClosed, sequenceID);
		}

		if (sequenceClosed) {
			FaultData data = new FaultData();
			int SOAPVersion = SandeshaUtil.getSOAPVersion(referenceMessage.getEnvelope());
			if (SOAPVersion == Sandesha2Constants.SOAPVersion.v1_1)
				data.setCode(SOAP11Constants.FAULT_CODE_SENDER);
			else
				data.setCode(SOAP12Constants.FAULT_CODE_SENDER);

			data.setSubcode(Sandesha2Constants.SOAPFaults.Subcodes.SEQUENCE_CLOSED);
			data.setReason(reason);

			if (log.isDebugEnabled())
				log.debug("Exit: FaultManager::checkForSequenceClosed, sequence closed");
			return getFault(referenceRMMessage, data, referenceRMMessage.getAddressingNamespaceValue(), storageManager);
		}

		if (log.isDebugEnabled())
			log.debug("Exit: FaultManager::checkForSequenceClosed");
		return null;

	}

	/**
	 * Returns a RMMessageContext for the fault message. Data for generating the
	 * fault is given in the data parameter.
	 * 
	 * @param referenceRMMsgContext
	 * @param data
	 * @return
	 * @throws SandeshaException
	 */
	public RMMsgContext getFault(RMMsgContext referenceRMMsgContext, FaultData data, String addressingNamespaceURI,
			StorageManager storageManager) throws SandeshaException {
		if (log.isDebugEnabled())
			log.debug("Enter: FaultManager::getFault");

		try {
			MessageContext referenceMessage = referenceRMMsgContext.getMessageContext();
			ConfigurationContext configCtx = referenceRMMsgContext.getConfigurationContext();

			// This is to hack to remove NPE. TODO remove this.
			if (referenceMessage.getServiceGroupContext() == null) {
				ServiceGroupContext serviceGroupContext = new ServiceGroupContext(referenceMessage
						.getConfigurationContext(), referenceMessage.getAxisServiceGroup());
				referenceMessage.setServiceGroupContext(serviceGroupContext);
			}
			if (referenceMessage.getServiceContext() == null) {
				ServiceContext serviceContext = new ServiceContext(referenceMessage.getAxisService(), referenceMessage
						.getServiceGroupContext());
				referenceMessage.setServiceContext(serviceContext);
			}

			// end hack

			//TODO this fails when the in message is in only. Fault is thrown at the InOnlyAxisOperation
			MessageContext faultMsgContext = Utils.createOutMessageContext(referenceMessage);

			// setting contexts.
			faultMsgContext.setAxisServiceGroup(referenceMessage.getAxisServiceGroup());
			faultMsgContext.setAxisService(referenceMessage.getAxisService());
			faultMsgContext.setAxisServiceGroup(referenceMessage.getAxisServiceGroup());
			faultMsgContext.setServiceGroupContext(referenceMessage.getServiceGroupContext());
			faultMsgContext.setServiceGroupContextId(referenceMessage.getServiceGroupContextId());
			faultMsgContext.setServiceContext(referenceMessage.getServiceContext());
			faultMsgContext.setServiceContextID(referenceMessage.getServiceContextID());

			AxisOperation operation = AxisOperationFactory.getAxisOperation(WSDL20_2004Constants.MEP_CONSTANT_OUT_ONLY);

			OperationContext operationContext = new OperationContext(operation);

			faultMsgContext.setAxisOperation(operation);
			faultMsgContext.setOperationContext(operationContext);

			String acksToStr = null;
			if (referenceRMMsgContext.getMessageType() == Sandesha2Constants.MessageTypes.CREATE_SEQ) {
				CreateSequence createSequence = (CreateSequence) referenceRMMsgContext
						.getMessagePart(Sandesha2Constants.MessageParts.CREATE_SEQ);
				acksToStr = createSequence.getAcksTo().getAddress().getEpr().getAddress();
			} else {
				SequencePropertyBeanMgr seqPropMgr = storageManager.getSequencePropertyBeanMgr();

				// TODO get the acksTo value using the property key.

				String sequenceId = data.getSequenceId();
				SequencePropertyBean acksToBean = seqPropMgr.retrieve(sequenceId,
						Sandesha2Constants.SequenceProperties.ACKS_TO_EPR);
				if (acksToBean != null) {
					EndpointReference epr = new EndpointReference(acksToBean.getValue());
					if (epr != null)
						acksToStr = epr.getAddress();
				}
			}

			String anonymousURI = SpecSpecificConstants.getAddressingAnonymousURI(addressingNamespaceURI);

			if (acksToStr != null && !acksToStr.equals(anonymousURI)) {
				faultMsgContext.setTo(new EndpointReference(acksToStr));
			}

			int SOAPVersion = SandeshaUtil.getSOAPVersion(referenceMessage.getEnvelope());
			SOAPFaultEnvelopeCreator.addSOAPFaultEnvelope(faultMsgContext, SOAPVersion, data, referenceRMMsgContext
					.getRMNamespaceValue());

			RMMsgContext faultRMMsgCtx = MsgInitializer.initializeMessage(faultMsgContext);

			if (log.isDebugEnabled())
				log.debug("Exit: FaultManager::getFault");
			return faultRMMsgCtx;

		} catch (AxisFault e) {
			throw new SandeshaException(e.getMessage(), e);
		}
	}

}
