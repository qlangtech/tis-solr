/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 * This program is free software: you can use, redistribute, and/or modify
 * it under the terms of the GNU Affero General Public License, version 3
 * or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.qlangtech.tis.order.dump.task;

import com.qlangtech.tis.fullbuild.indexbuild.IDumpTable;
import com.qlangtech.tis.sql.parser.tuple.creator.EntityName;

/**
 * @author 百岁（baisui@qlangtech.com）
 * @date 2021-03-03 14:32
 */
public interface ITestDumpCommon {

    String DB_EMPLOYEES = "employees";
    String TABLE_EMPLOYEES = "employees";
    String TOPOLOGY_EMPLOYEES = "employees";
    String INDEX_COLLECTION = "search4" + TABLE_EMPLOYEES;

    default IDumpTable getEmployeeTab() {
        return EntityName.parse(DB_EMPLOYEES + "." + TABLE_EMPLOYEES);
    }
}
