"""Verify locked libraries can build the specified API, graph and provider boundary."""

import importlib.metadata
import sys
from typing import TypedDict

import boto3
from fastapi import FastAPI
from fastapi.testclient import TestClient
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, ConfigDict


def test_python_312():
    assert sys.version_info[:3] == (3, 12, 12)


def test_fastapi_pydantic_httpx_strict_request_compatibility():
    class Input(BaseModel):
        model_config = ConfigDict(extra="forbid")
        value: str

    application = FastAPI()

    @application.post("/compatibility")
    def echo(body: Input) -> Input:
        return body

    with TestClient(application) as client:
        assert client.post("/compatibility", json={"value": "test"}).json() == {"value": "test"}
        assert client.post("/compatibility", json={"value": "test", "unexpected": 1}).status_code == 422


def test_langgraph_typed_state_compiles():
    class State(TypedDict):
        visited: bool

    graph = StateGraph(State)
    graph.add_node("compatibility", lambda state: {"visited": True})
    graph.add_edge(START, "compatibility")
    graph.add_edge("compatibility", END)
    assert graph.compile().invoke({"visited": False}) == {"visited": True}


def test_bedrock_sdk_has_required_operation_models_without_network():
    # Load SDK models only; no credentials, client, inference or provider call.
    model = boto3.Session()._session.get_service_model("bedrock-runtime")
    assert {"Converse", "ConverseStream", "InvokeModel"} <= set(model.operation_names)
    assert importlib.metadata.version("nigson-ai") == "0.1.0"
