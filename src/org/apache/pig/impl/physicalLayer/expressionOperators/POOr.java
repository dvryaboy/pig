/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pig.impl.physicalLayer.expressionOperators;

import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.DataType;
import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.physicalLayer.POStatus;
import org.apache.pig.impl.physicalLayer.Result;
import org.apache.pig.impl.physicalLayer.plans.ExprPlanVisitor;
import org.apache.pig.impl.plan.VisitorException;

/**
 * Boolean or operator.
 */

public class POOr extends BinaryComparisonOperator {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public POOr(OperatorKey k) {
        super(k);
    }

    public POOr(OperatorKey k, int rp) {
        super(k, rp);
    }

    @Override
    public void visit(ExprPlanVisitor v) throws VisitorException {
        v.visitOr(this);
    }

    @Override
    public String name() {
        return "Or" + "[" + DataType.findTypeName(resultType) + "]" +" - " + mKey.toString();
    }

    @Override
    public Result getNext(Boolean b) throws ExecException {
        byte status;
        Result res;
        Boolean left = null, right = null;
        res = lhs.getNext(left);
        status = res.returnStatus;
        if(status != POStatus.STATUS_OK) {
            return res;
        }
        left = (Boolean)res.result;
        // Short circuit.
        if (left) return res;
        
        res = rhs.getNext(right);
        status = res.returnStatus;
        if(status != POStatus.STATUS_OK) {
            return res;
        }
        right = (Boolean)res.result;
        
        res.result = new Boolean(left || right);
        return res;
    }
}
