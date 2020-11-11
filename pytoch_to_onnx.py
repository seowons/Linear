# -*- coding: utf-8 -*-
"""
Created on Thu Nov 12 00:34:46 2020

@author: sue
"""

import onnx
import torch

example_input = get_example_input("‪C:\\Users\\user\\Desktop\\cap\\model (3).pt") # exmample for the forward pass input 
pytorch_model = get_pytorch_model(‪)
ONNX_PATH="./my_model.onnx"

torch.onnx.export(
    model=pytorch_model,
    args=example_input, 
    f=ONNX_PATH, # where should it be saved
    verbose=False,
    export_params=True,
    do_constant_folding=False,  # fold constant values for optimization
    # do_constant_folding=True,   # fold constant values for optimization
    input_names=['input'],
    output_names=['output']
)
onnx_model = onnx.load(ONNX_PATH)
onnx.checker.check_model(onnx_model)