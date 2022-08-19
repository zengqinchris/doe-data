package cn.doitedu.rtmk.rulemodel.caculator.groovy

import cn.doitedu.rtmk.common.interfaces.RuleConditionCalculator
import cn.doitedu.rtmk.common.pojo.UserEvent
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import redis.clients.jedis.Jedis

class RuleModel_01_Calculator_Java implements RuleConditionCalculator {

    private Jedis jedis;
    private JSONObject ruleDefineParamJsonObject;
    private JSONObject eventCountConditionParam;
    private String ruleId;


    /**
     * 规则运算机的初始化方法
     *
     * @param jedis                     连接redis的客户端
     * @param ruleDefineParamJsonObject 整个规则的json参数
     */
    @Override
    public void init(Jedis jedis, JSONObject ruleDefineParamJsonObject) {
        this.jedis = jedis;
        this.ruleDefineParamJsonObject = ruleDefineParamJsonObject;

        // 取到规则的标识id
        ruleId = ruleDefineParamJsonObject.getString("ruleId");

        // 取到规则的事件次数条件
        this.eventCountConditionParam = ruleDefineParamJsonObject.getJSONObject("actionCountCondition");

    }

    /**
     * 规则条件的实时运算逻辑
     *
     * @param userEvent 输入的一次用户行为
     */
    @Override
    public void calc(UserEvent userEvent) {

        JSONArray eventParams = eventCountConditionParam.getJSONArray("eventParams");
        int size = eventParams.size();

        // 遍历事件次数条件中的每一个事件参数
        for (int i = 0; i < size; i++) {

            // 取出事件条件列表中的：一个事件条件参数
            JSONObject eventParam = eventParams.getJSONObject(i);

            // 取出本条件的条件id
            Integer conditionId = eventParam.getInteger("conditionId");

            // 1. 判断当前输入的事件id，是否等于：条件参数中要求的事件id
            if (userEvent.getEventId().equals(eventParam.getString("eventId"))) {

                // 2. 判断当前输入的事件的各个事件属性，是否满足：条件中要求的各个属性参数
                JSONArray attributeParams = eventParam.getJSONArray("attributeParams");
                boolean b = judgeEventAttribute(userEvent, attributeParams);


                // 3. 判断事件的时间，是否满足：条件参数中要求的计算时间窗口
                // TODO

                if (b) {
                    // 4. 如果代码到了这里，说明输入事件的id和属性，都与条件参数的要求相吻合了
                    // 那么，就需要去redis中，给这个用户的，这个规则的，这个条件的次数+1
                    jedis.hincrBy(ruleId + ":" + conditionId, userEvent.getGuid() + "", 1);
                }
            }
        }


    }


    /**
     * 判断某用户是否满足了该条件
     *
     * @param guid 用户id
     * @return 是否满足该条件
     * <p>
     * " res0 && (res1 || res2) "
     * " res0 && res2  || res3 && res4 "
     * " res0 || res1 "
     * " res0 && res1 && res2 "
     */
    @Override
    public boolean isMatch(int guid) {
        JSONArray eventParams = eventCountConditionParam.getJSONArray("eventParams");


        // 取出一个事件条件参数
        JSONObject eventParam_0 = eventParams.getJSONObject(0);
        // 取出该条件的条件id
        Integer conditionId_0 = eventParam_0.getInteger("conditionId");

        // 取出该事件次数条件的要求的发生次数
        Integer eventCountParam_0 = eventParam_0.getInteger("eventCount");

        // 去redis中查询该用户，该条件的实际发生次数
        String realCountStr_0 = jedis.hget(ruleId + ":" + conditionId_0, guid + "");
        int realCount_0 = Integer.parseInt(realCountStr_0 == null ? "0" : realCountStr_0);

        // 判断，该条件是否已经满足
        boolean res_0 = realCount_0 >= eventCountParam_0 ;




        return  res_0;
    }


    /**
     * 判断事件属性是否满足条件参数要求的方法
     *
     * @param userEvent       输入的用户行为
     * @param attributeParams 规则条件中的属性参数
     * @return 属性是否匹配
     */
    private boolean judgeEventAttribute(UserEvent userEvent, JSONArray attributeParams) {
        // 对每一个属性条件进行判断
        for (int j = 0; j < attributeParams.size(); j++) {
            // 取出一个属性参数
            JSONObject attributeParam = attributeParams.getJSONObject(j);

            String paramAttributeName = attributeParam.getString("attributeName");
            String paramCompareType = attributeParam.getString("compareType");
            String paramValue = attributeParam.getString("compareValue");

            String eventAttributeValue = userEvent.getProperties().get(paramAttributeName);

            if ("=" == paramCompareType && !(paramValue == eventAttributeValue)) {
                return false;
            }

            if (">" == paramCompareType && !(paramValue > eventAttributeValue)) {
                return false;
            }

            if ("<" == paramCompareType && !(paramValue < eventAttributeValue)) {
                return false;
            }

            if ("<=" == paramCompareType && !(paramValue <= eventAttributeValue)) {
                return false;
            }

            if (">=" == paramCompareType && !(paramValue >= eventAttributeValue)) {
                return false;
            }

        }
        return true;
    }


}