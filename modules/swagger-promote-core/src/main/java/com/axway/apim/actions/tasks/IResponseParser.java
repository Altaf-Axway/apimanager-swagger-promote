package com.axway.apim.actions.tasks;

import org.apache.http.HttpResponse;

import com.axway.apim.lib.AppException;
import com.fasterxml.jackson.databind.JsonNode;

public interface IResponseParser {
	public JsonNode parseResponse(HttpResponse response) throws AppException;
}
