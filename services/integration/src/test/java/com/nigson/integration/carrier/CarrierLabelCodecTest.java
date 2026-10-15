package com.nigson.integration.carrier;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.nigson.shared.events.EventCodec;
import com.nigson.shared.problem.Problem;
import static org.assertj.core.api.Assertions.*;

class CarrierLabelCodecTest {
 static byte[] pdf(int pages,boolean script,boolean encrypted)throws Exception{try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()){for(int i=0;i<pages;i++)doc.addPage(new PDPage(new PDRectangle(288,432)));if(script)doc.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('synthetic');"));if(encrypted)doc.protect(new StandardProtectionPolicy("synthetic-owner","synthetic-reader",new AccessPermission()));doc.save(bytes);return bytes.toByteArray();}}
 static ObjectNode output(byte[]... labels){var pieces=new ArrayList<Object>();for(int i=0;i<labels.length;i++)pieces.add(Map.of("trackingNumber",String.valueOf(123456789012L+i),"packageDocuments",List.of(Map.of("contentType","LABEL","docType","PDF","encodedLabel",Base64.getEncoder().encodeToString(labels[i])))));return (ObjectNode)EventCodec.JSON.valueToTree(Map.of("transactionShipments",List.of(Map.of("masterTrackingNumber","123456789012","pieceResponses",pieces))));}
 @Test void mergesAllLabelsDeterministicallyWithoutChangingPageGeometry()throws Exception{var output=output(pdf(1,false,false),pdf(1,false,false));var first=CarrierLabelCodec.decode(output,"123456789012",2);var array=(tools.jackson.databind.node.ArrayNode)output.path("transactionShipments").path(0).path("pieceResponses");var swapped=array.remove(0);array.add(swapped);var second=CarrierLabelCodec.decode(output,"123456789012",2);assertThat(second.checksum()).isEqualTo(first.checksum());assertThat(second.bytes()).isEqualTo(first.bytes());assertThat(first.pages()).isEqualTo(2);try(var parsed=Loader.loadPDF(first.bytes())){assertThat(parsed.getNumberOfPages()).isEqualTo(2);assertThat(parsed.getPage(0).getMediaBox().getWidth()).isEqualTo(288);assertThat(parsed.getPage(0).getMediaBox().getHeight()).isEqualTo(432);}byte[] altered=first.bytes();altered[0]=0;assertThat(first.bytes()[0]).isEqualTo((byte)'%');assertThat(first.toString()).isEqualTo("CarrierLabel[protected]");}
 @Test void activeEncryptedMalformedOrExcessivePagePdfIsRejected()throws Exception{for(byte[] bytes:List.of(pdf(1,true,false),pdf(1,false,true),pdf(5,false,false),"%PDF-broken".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))assertThatThrownBy(()->CarrierLabelCodec.decode(output(bytes),"123456789012",1)).isInstanceOf(Problem.Failure.class);}
 @Test void missingDuplicateOrForeignPieceCannotProducePartialLabel()throws Exception{var valid=output(pdf(1,false,false),pdf(1,false,false));assertThatThrownBy(()->CarrierLabelCodec.decode(valid,"123456789012",1)).isInstanceOf(Problem.Failure.class);var pieces=(tools.jackson.databind.node.ArrayNode)valid.path("transactionShipments").path(0).path("pieceResponses");((ObjectNode)pieces.path(1)).put("trackingNumber","123456789012");assertThatThrownBy(()->CarrierLabelCodec.decode(valid,"123456789012",2)).isInstanceOf(Problem.Failure.class);assertThatThrownBy(()->CarrierLabelCodec.decode(valid,"999999999999",2)).isInstanceOf(Problem.Failure.class);}
 @Test void urlOnlyUnknownDocumentTypeAndOversizedBase64AreNeverFetched()throws Exception{var output=output(pdf(1,false,false));var document=(ObjectNode)output.path("transactionShipments").path(0).path("pieceResponses").path(0).path("packageDocuments").path(0);document.remove("encodedLabel");document.put("url","https://synthetic-untrusted.example.invalid/private");assertThatThrownBy(()->CarrierLabelCodec.decode(output,"123456789012",1)).isInstanceOf(Problem.Failure.class);document.put("encodedLabel",Base64.getEncoder().encodeToString(pdf(1,false,false))).put("docType","PNG");assertThatThrownBy(()->CarrierLabelCodec.decode(output,"123456789012",1)).isInstanceOf(Problem.Failure.class);document.put("docType","PDF").put("encodedLabel","a".repeat(14_000_000));assertThatThrownBy(()->CarrierLabelCodec.decode(output,"123456789012",1)).isInstanceOf(Problem.Failure.class);}
}
